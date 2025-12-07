# 🃏 Android Blackjack — Kotlin & Jetpack ViewBinding

A polished, arcade-style **single-player** Blackjack game built entirely in Kotlin.  
The architecture is already designed so that **future multiplayer support can be added easily**  
(using PlayerController & RoundManager flow).

Includes smooth animations, split support (including split-aces rule), dealer auto-play,
ace-choice dialogs, and full UI overlays.

---

## ✨ Current Features (Single Player)

### 🎮 Gameplay
- Full classic Blackjack rules
- Betting system with money tracking
- Natural Blackjack detection (player & dealer)
- Push, win, lose, and blackjack payouts
- Split support (including **split aces auto-stand rule**)
- Ace value selection (1 or 11)
- Dealer auto-play using real casino logic

### 🃑 Card & Engine Logic
- Custom `GameEngine` controls:
  - Hit / Stand
  - Split & split-aces restrictions
  - Ace manual value selection
  - Auto-switch from Hand1 → Hand2
  - Dealer draw logic
- Automatic deck reshuffle when running low

### 🎨 UI & Animations
- Slide-in card animations  
- Dealer hidden-card flip  
- Split-hand layout with active-hand highlighting  
- Win / Lose / Push / Blackjack overlays  
- Full ViewBinding support  
- Smooth event-driven updates

### 🧠 Architecture
- **Clean separation of UI + logic**
- `RoundManager` controls the entire flow of each round
- `GameEngine` emits typed events to the UI
- Modular UI components:
  - `HandViewManager`
  - `UIManager`
  - `OverlayManager`
  - `AceDialogManager`

---

## 🔮 Future Multiplayer Support
Although the current version is **single-player**, the foundation is prepared for multiplayer:

- `PlayerController` already manages players  
- `RoundManager` cycles through “currentPlayer”  
- Money, bets, overlays, and round transitions are player-aware  

Adding multiplayer in the future will be straightforward.

---

## 📂 Project Structure

/com.example.blackjack
│
├── GameEngine.kt           # Pure game logic (hit, stand, split, scoring)
├── RoundManager.kt         # Controls each round & communicates with UI
├── HandViewManager.kt      # Card UI & animations
├── UIManager.kt            # Buttons, money, labels, scores
├── OverlayManager.kt       # Win/Lose/Push/Blackjack overlays
├── AceDialogManager.kt     # Ace selection popup
│
├── Deck.kt / Card.kt       # Card models + deck behavior
├── PlayerController.kt     # Prepared for multiplayer expansion
│
└── ActivityGameboardBinding # Auto-generated from XML

---

## 🚀 Gameplay Flow

### **1. Round start**
- Deduct bet  
- Deal two cards to player, two to dealer  
- Show dealer's second card face-down  

### **2. Player actions**
- Hit  
- Stand  
- Split (if allowed)  

Ace dialog appears automatically when needed.

### **3. Split Mode**
- Auto-switch to Hand2 after Hand1 finishes  
- Split aces → each hand gets exactly **one** card (auto-stand)

### **4. Dealer phase**
- Flip hidden card  
- Dealer draws until ≥ 17  
- Round result emitted  

### **5. End of round**
- Overlay shown  
- Money updated  
- Next round ready  

---

## 🧪 Potential Future Features
- Multiplayer (already prepared)
- Double-down  
- Insurance  
- Surrender  
- Sound effects  
- Statistics / history screen  
- Custom deck graphics  

---

## 📜 License
Free for educational or personal use.  
For commercial use, please contact the author.

---

## ❤️ Credits
Made with Kotlin, animations, and a love for clean architecture & Blackjack.
