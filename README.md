# 😈 DemoniTalk (v1.0 - Demonic Edition)

DemoniTalk is an advanced voice assistant and automation engine for Android that requires root privileges. It allows you to map custom voice commands ("Triggers") to instantly execute console scripts, touch simulation macros, and low-level system process controls via a floating interface.

---

## ✨ Key Features

* **🎙️ Intelligent Voice Assistant:** Integrated listening module via an interactive floating button (overlay) that runs over any app or game.
* **⚡ Root-Powered Automation (Custom Triggers):**
    * **Process Control:** Immediate force-stop of any app (`am force-stop`) or task termination by PID.
    * **Interactive Touch Simulation:** Creation of complex macros by chaining tap coordinates (`input tap`), swipes (`input swipe`), and timers (`sleep`).
    * **Keyevent Injection:** Execution of native system actions—such as Back, Home, or Recent Apps—via console commands.
* **🔄 Dynamic Capture Modes:**
    * **Continuous Mode:** The assistant remains in a state of active listening, waiting for chained voice commands.
    * **Manual Mode:** Push-to-activate functionality to optimize resource and battery usage.
* **🎨 "Demonic Edition" Interface:** Aggressive yet clean dark design with theme support (Light/Dark Mode), command dictionary management (Import/Export configurations), and dynamic visual alerts.

---

## 🛠️ Technical Stack & Requirements

* **Infrastructure:** Requires full Superuser access (Magisk / KernelSU) for command injection into the Android terminal.
* **Accessibility / Window Services:** Screen overlay permission to display the floating microphone widget in real-time.
* **Ecosystem:** Includes a custom parser to import and export your macro lists via local configuration files. ---

## 📄 License and Copyright

Copyright © 2026. All rights reserved. The source code of this application is the private property of the developer. Unauthorized reproduction, distribution, or modification of this software is prohibited.

*Developed with 🧡 by an independent developer.*


<p align="center">
  <img src="https://github.com/user-attachments/assets/e728adac-3cd9-4ed4-9d7b-88f2da9cca68" width="30%" />
  <img src="https://github.com/user-attachments/assets/1feaade7-f9d6-4ce1-8829-be88116456e5" width="30%" />
</p>
<p align="center">
  <img src="https://github.com/user-attachments/assets/a17f967a-f317-4bd5-bbbe-c3267ca96bf1" width="30%" />
  <img src="https://github.com/user-attachments/assets/9be6a92b-b8c9-4204-9fe3-49fd2b3567fa" width="30%" />  
</p>

![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=flat&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/Android-3DDC84?style=flat&logo=android&logoColor=white)
