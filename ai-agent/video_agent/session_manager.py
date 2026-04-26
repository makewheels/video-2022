"""MongoDB-based session manager for multi-turn conversations."""

from __future__ import annotations

import os
from datetime import datetime, timedelta, timezone
from typing import Any

from motor.motor_asyncio import AsyncIOMotorClient
from pymongo import ASCENDING


class SessionManager:
    """Manages conversation sessions with MongoDB persistence."""

    def __init__(self, mongo_uri: str | None = None, db_name: str = "video_agent"):
        self.mongo_uri = mongo_uri or os.getenv("MONGODB_URI", "mongodb://localhost:27017")
        self.db_name = db_name
        self.client: AsyncIOMotorClient | None = None
        self.db = None
        self.sessions = None

    async def connect(self):
        """Initialize MongoDB connection."""
        if self.client is None:
            self.client = AsyncIOMotorClient(self.mongo_uri)
            self.db = self.client[self.db_name]
            self.sessions = self.db.sessions
            # Create indexes
            await self.sessions.create_index([("session_id", ASCENDING)], unique=True)
            await self.sessions.create_index([("updated_at", ASCENDING)], expireAfterSeconds=86400 * 7)  # 7 days TTL

    async def close(self):
        """Close MongoDB connection."""
        if self.client:
            self.client.close()
            self.client = None

    async def get_session(self, session_id: str) -> dict[str, Any] | None:
        """Retrieve session by ID."""
        await self.connect()
        return await self.sessions.find_one({"session_id": session_id})

    async def create_session(self, session_id: str, metadata: dict[str, Any] | None = None) -> dict[str, Any]:
        """Create a new session."""
        await self.connect()
        session = {
            "session_id": session_id,
            "messages": [],
            "metadata": metadata or {},
            "created_at": datetime.now(timezone.utc),
            "updated_at": datetime.now(timezone.utc),
        }
        await self.sessions.insert_one(session)
        return session

    async def update_session(self, session_id: str, messages: list[dict[str, Any]], metadata: dict[str, Any] | None = None):
        """Update session messages and metadata."""
        await self.connect()
        update_data = {
            "messages": messages,
            "updated_at": datetime.utcnow(),
        }
        if metadata:
            update_data["metadata"] = metadata
        await self.sessions.update_one({"session_id": session_id}, {"$set": update_data}, upsert=True)

    async def append_message(self, session_id: str, message: dict[str, Any]):
        """Append a single message to session."""
        await self.connect()
        await self.sessions.update_one(
            {"session_id": session_id},
            {"$push": {"messages": message}, "$set": {"updated_at": datetime.now(timezone.utc)}},
            upsert=True,
        )

    async def get_messages(self, session_id: str, limit: int | None = None) -> list[dict[str, Any]]:
        """Get conversation history for a session."""
        session = await self.get_session(session_id)
        if not session:
            return []
        messages = session.get("messages", [])
        if limit:
            return messages[-limit:]
        return messages

    async def delete_session(self, session_id: str):
        """Delete a session."""
        await self.connect()
        await self.sessions.delete_one({"session_id": session_id})

    async def cleanup_old_sessions(self, days: int = 7):
        """Manually cleanup sessions older than specified days."""
        await self.connect()
        cutoff = datetime.now(timezone.utc) - timedelta(days=days)
        result = await self.sessions.delete_many({"updated_at": {"$lt": cutoff}})
        return result.deleted_count
