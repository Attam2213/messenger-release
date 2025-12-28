from fastapi import FastAPI, HTTPException, WebSocket, WebSocketDisconnect, UploadFile, File
from fastapi.responses import FileResponse
from pydantic import BaseModel
from typing import List, Dict
import time
import hashlib
import json
import os
import uuid
import shutil

app = FastAPI()

# Create files directory if not exists
FILES_DIR = "server_files"
os.makedirs(FILES_DIR, exist_ok=True)

# In-memory storage: recipient_hash -> List[Message]
mailbox: Dict[str, List[Dict]] = {}
# Active WebSocket connections: recipient_hash -> WebSocket
active_connections: Dict[str, WebSocket] = {}

class Message(BaseModel):
    to_hash: str      # SHA-256 hash of recipient's public key
    from_key: str     # Full public key of sender
    content: str      # Encrypted content (Base64)
    timestamp: int

@app.post("/upload")
async def upload_file(file: UploadFile = File(...)):
    try:
        file_id = str(uuid.uuid4())
        file_path = os.path.join(FILES_DIR, file_id)
        
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
            
        print(f"File uploaded: {file_id}, size: {os.path.getsize(file_path)} bytes")
        return {"fileId": file_id, "size": os.path.getsize(file_path), "filename": file.filename}
    except Exception as e:
        print(f"Upload error: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/files/{file_id}")
async def download_file(file_id: str):
    file_path = os.path.join(FILES_DIR, file_id)
    if not os.path.exists(file_path):
        raise HTTPException(status_code=404, detail="File not found")
    return FileResponse(file_path)

@app.websocket("/ws/{client_hash}")
async def websocket_endpoint(websocket: WebSocket, client_hash: str):
    await websocket.accept()
    active_connections[client_hash] = websocket
    print(f"Client connected: {client_hash[:8]}...")
    
    # Send pending messages immediately
    if client_hash in mailbox and mailbox[client_hash]:
        pending = mailbox[client_hash]
        print(f"Sending {len(pending)} pending messages to {client_hash[:8]}...")
        for msg in pending:
            await websocket.send_json(msg)
        mailbox[client_hash] = []
        
    try:
        while True:
            # Keep connection alive, maybe receive ACKs or heartbeats
            data = await websocket.receive_text()
            if data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        print(f"Client disconnected: {client_hash[:8]}...")
        if client_hash in active_connections:
            del active_connections[client_hash]
    except Exception as e:
        print(f"WebSocket error for {client_hash[:8]}: {e}")
        if client_hash in active_connections:
            del active_connections[client_hash]

@app.post("/send")
async def send_message(msg: Message):
    # Try to send via WebSocket first
    if msg.to_hash in active_connections:
        try:
            websocket = active_connections[msg.to_hash]
            await websocket.send_json(msg.dict())
            print(f"Direct WS delivery to {msg.to_hash[:8]}... from {msg.from_key[:10]}...")
            return {"status": "sent", "method": "websocket", "server_time": int(time.time())}
        except Exception as e:
            print(f"WS delivery failed: {e}. Fallback to mailbox.")
            if msg.to_hash in active_connections:
                del active_connections[msg.to_hash]

    if msg.to_hash not in mailbox:
        mailbox[msg.to_hash] = []
    
    mailbox[msg.to_hash].append(msg.dict())
    print(f"Message stored for {msg.to_hash[:8]}... from {msg.from_key[:10]}...")
    return {"status": "sent", "method": "mailbox", "server_time": int(time.time())}

@app.get("/check/{recipient_hash}")
def check_messages(recipient_hash: str):
    messages = mailbox.get(recipient_hash, [])
    if messages:
        mailbox[recipient_hash] = []
        print(f"Delivered {len(messages)} messages to {recipient_hash[:8]}...")
    return messages

@app.get("/")
def root():
    return {
        "status": "running", 
        "active_mailboxes": len(mailbox),
        "active_connections": len(active_connections)
    }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
