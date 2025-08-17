# Distributed Chat System 💬

A multi-user **chat application** built with **Java, MySQL, and TCP sockets**.  
Implements **OS (multithreading), CN (client-server networking), DBMS (persistence), and OOP (design patterns)** in one project.

---

## ✨ Features
- User registration/login with **invite tokens**
- **Chat rooms** and **private messaging**
- Persistent **chat history** in MySQL
- **Multithreaded server** handling multiple clients
- Secure password storage using **PBKDF2**
- Commands:
  - `/register <user> <pass> <token>`
  - `/login <user> <pass>`
  - `/join <room>`
  - `/msg <room> <text>`
  - `/pm <user> <text>`
  - `/history <target> [limit]`

---

## ⚙️ Tech Stack
- **Java** (Sockets, Threads, JDBC)
- **MySQL** (Schema + persistence)
- **Maven** (dependencies & build)
- **Gson** (JSON serialization)

---

## 🚀 Setup

