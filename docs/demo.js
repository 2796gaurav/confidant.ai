// Interactive Chat Demo for Confidant AI
// Showcases all features with realistic conversations

const demoConversations = [
  {
    id: 'greeting',
    title: '💬 Natural Conversation',
    messages: [
      { role: 'user', text: 'Hey! How are you?' },
      { role: 'assistant', text: 'Hey! I\'m doing great, thanks for asking! 😊 How can I help you today?' }
    ]
  },
  {
    id: 'web-search',
    title: '🔍 Web Search',
    messages: [
      { role: 'user', text: 'What\'s the Bitcoin price today?' },
      { role: 'assistant', text: '🔍 Searching the web...', typing: true },
      { role: 'tool', text: '⚙️ Tool: web_search\nQuery: "Bitcoin price today"\nDeep fetch: enabled', collapsed: true },
      { role: 'assistant', text: 'Based on current market data, Bitcoin (BTC) is trading at approximately $51,234 USD as of today.\n\n📊 Key Stats:\n• 24h Change: +2.3% ($1,156)\n• Market Cap: $1.01 trillion\n• 24h Volume: $28.4 billion\n\nThe price has been showing bullish momentum following recent institutional adoption news.\n\n🔗 Sources: CoinMarketCap, CoinGecko' }
    ]
  },
  {
    id: 'save-note',
    title: '📝 Save Notes',
    messages: [
      { role: 'user', text: 'Save note: WiFi password is SecurePass2024!' },
      { role: 'assistant', text: '⚙️ Saving...', typing: true },
      { role: 'tool', text: '⚙️ Tool: save_note\nTitle: "WiFi password"\nContent: "WiFi password is SecurePass2024!"\nCategory: passwords (auto-detected)\nPriority: normal', collapsed: true },
      { role: 'assistant', text: '✅ Got it! I\'ve saved your WiFi password securely.\n\n📝 Note Details:\n• Title: WiFi password\n• Category: passwords\n• Saved: Just now\n\nYou can retrieve it anytime by asking "What\'s my WiFi password?"' }
    ]
  },
  {
    id: 'retrieve-note',
    title: '🔎 Retrieve Notes',
    messages: [
      { role: 'user', text: 'What\'s my WiFi password?' },
      { role: 'assistant', text: '🔍 Searching notes...', typing: true },
      { role: 'tool', text: '⚙️ Tool: retrieve_notes\nQuery: "WiFi password"\nResults: 1 note found\nSearch method: BM25 + fuzzy matching', collapsed: true },
      { role: 'assistant', text: '🔐 Found your WiFi password:\n\n**WiFi password**\nSecurePass2024!\n\n📅 Saved: 2 hours ago\n🏷️ Category: passwords' }
    ]
  },
  {
    id: 'multi-turn',
    title: '🔄 Multi-Turn Conversation',
    messages: [
      { role: 'user', text: 'Save a reminder' },
      { role: 'assistant', text: 'Sure! What would you like to be reminded about?' },
      { role: 'user', text: 'Call mom tomorrow at 3pm' },
      { role: 'assistant', text: '⚙️ Saving reminder...', typing: true },
      { role: 'tool', text: '⚙️ Tool: save_note\nTitle: "Call mom"\nContent: "Call mom tomorrow at 3pm"\nCategory: reminders\nReminder: Tomorrow 3:00 PM\nPriority: normal', collapsed: true },
      { role: 'assistant', text: '✅ Perfect! I\'ve set a reminder for you.\n\n⏰ Reminder Details:\n• Task: Call mom\n• When: Tomorrow at 3:00 PM\n• Category: reminders\n\nI\'ll notify you when it\'s time!' }
    ]
  },
  {
    id: 'personalized',
    title: '🎯 Personalized Response',
    messages: [
      { role: 'system', text: '👤 User Interests: Technology, AI, Programming', small: true },
      { role: 'user', text: 'Tell me about the latest AI developments' },
      { role: 'assistant', text: '🔍 Searching for latest AI news...', typing: true },
      { role: 'tool', text: '⚙️ Tool: web_search\nQuery: "latest AI developments 2026"\nPersonalized: Yes (tech focus)\nDeep fetch: enabled', collapsed: true },
      { role: 'assistant', text: '🤖 Here are the latest AI developments that might interest you:\n\n**1. GPT-5 Release**\nOpenAI announced GPT-5 with 10T parameters, showing significant improvements in reasoning and coding capabilities.\n\n**2. On-Device AI Breakthrough**\nNew quantization techniques enable running 70B models on smartphones with <4GB RAM.\n\n**3. AI Coding Assistants**\nGitHub Copilot X now generates entire applications from natural language descriptions.\n\n**4. Regulatory Updates**\nEU AI Act officially enacted, setting global standards for AI safety.\n\nGiven your interest in programming, you might find the on-device AI and coding assistant developments particularly relevant!\n\n🔗 Sources: TechCrunch, The Verge, ArXiv' }
    ]
  },
  {
    id: 'proactive',
    title: '🔔 Proactive Notification',
    messages: [
      { role: 'system', text: '📱 Notification detected: "Your Amazon package has been delivered"', small: true },
      { role: 'assistant', text: '📦 Hey! I noticed your Amazon package was just delivered. Would you like me to:\n\n1. Save a note to check it when you get home?\n2. Set a reminder to bring it inside?\n3. Just acknowledge it?\n\nLet me know if you need anything!' },
      { role: 'user', text: 'Save a note' },
      { role: 'assistant', text: '✅ Done! I\'ve saved a note: "Amazon package delivered - check when home"\n\nYou can find it in your notes anytime.' }
    ]
  },
  {
    id: 'complex-query',
    title: '🧠 Complex Query',
    messages: [
      { role: 'user', text: 'Compare the top 3 smartphones under $500 with best cameras' },
      { role: 'assistant', text: '🔍 Researching smartphones...', typing: true },
      { role: 'tool', text: '⚙️ Tool: web_search\nQuery: "best smartphones under $500 camera 2026"\nDeep fetch: enabled\nSources: 5 articles analyzed', collapsed: true },
      { role: 'assistant', text: '📱 Here are the top 3 smartphones under $500 with excellent cameras:\n\n**1. Google Pixel 8a - $449**\n📸 Camera: 64MP main + 13MP ultrawide\n✨ Highlights: Google\'s computational photography, Night Sight, Magic Eraser\n⭐ Rating: 4.6/5\n\n**2. Samsung Galaxy A54 - $449**\n📸 Camera: 50MP main + 12MP ultrawide + 5MP macro\n✨ Highlights: OIS, 4K video, excellent low-light\n⭐ Rating: 4.5/5\n\n**3. OnePlus Nord 3 - $399**\n📸 Camera: 50MP main + 8MP ultrawide\n✨ Highlights: Fast charging, smooth performance, good daylight photos\n⭐ Rating: 4.4/5\n\n**Recommendation:** If camera quality is your priority, the Pixel 8a offers the best computational photography. For versatility, the Galaxy A54 with its triple camera setup is excellent.\n\n🔗 Sources: GSMArena, TechRadar, CNET' }
    ]
  },
  {
    id: 'context-aware',
    title: '💭 Context-Aware',
    messages: [
      { role: 'user', text: 'What\'s the weather in Tokyo?' },
      { role: 'assistant', text: '🔍 Checking weather...', typing: true },
      { role: 'assistant', text: '🌤️ Current weather in Tokyo:\n\n• Temperature: 18°C (64°F)\n• Condition: Partly cloudy\n• Humidity: 65%\n• Wind: 12 km/h NE\n\nForecast: Mild and pleasant today, slight chance of rain tomorrow.' },
      { role: 'user', text: 'Should I bring an umbrella?' },
      { role: 'assistant', text: 'Based on the Tokyo weather forecast, there\'s only a slight chance of rain tomorrow. I\'d recommend:\n\n☂️ **Keep a compact umbrella handy** - just in case, since there\'s some rain possibility.\n\nThe weather today is clear though, so you\'re good for now!' }
    ]
  },
  {
    id: 'tool-detection',
    title: '⚡ Automatic Tool Detection',
    messages: [
      { role: 'user', text: 'Bitcoin price' },
      { role: 'system', text: '🤖 Intent detected: WEB_SEARCH (pattern match - instant)', small: true },
      { role: 'assistant', text: '🔍 Searching...', typing: true },
      { role: 'assistant', text: 'Bitcoin is currently trading at $51,234 USD (+2.3% today).' },
      { role: 'user', text: 'Remember my gym membership is 12345' },
      { role: 'system', text: '🤖 Intent detected: SAVE_NOTE (pattern match - instant)', small: true },
      { role: 'assistant', text: '✅ Saved! Your gym membership number (12345) is now in your notes.' }
    ]
  }
];

class ChatDemo {
  constructor() {
    this.currentDemo = 0;
    this.messageIndex = 0;
    this.isPlaying = false;
    this.typingSpeed = 30;
    this.messageDelay = 1200;
    this.init();
  }

  init() {
    this.createDemoUI();
    this.loadDemo(0);
  }

  createDemoUI() {
    const demoSection = document.getElementById('demo-section');
    if (!demoSection) return;

    demoSection.innerHTML = `
      <div class="demo-container">
        <div class="demo-sidebar">
          <div class="demo-sidebar-header">
            <h3>💬 Try Examples</h3>
            <p>Click to see different features</p>
          </div>
          <div class="demo-list">
            ${demoConversations.map((demo, idx) => `
              <button class="demo-item ${idx === 0 ? 'active' : ''}" data-demo="${idx}">
                <span class="demo-item-title">${demo.title}</span>
                <span class="demo-item-count">${demo.messages.filter(m => m.role !== 'system').length} messages</span>
              </button>
            `).join('')}
          </div>
        </div>
        
        <div class="demo-chat">
          <div class="demo-chat-header">
            <div class="demo-chat-header-left">
              <div class="demo-avatar">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M12 2a5 5 0 1 0 0 10A5 5 0 0 0 12 2z"/>
                  <path d="M20 21a8 8 0 1 0-16 0"/>
                  <circle cx="18" cy="8" r="3" fill="currentColor" stroke="none"/>
                </svg>
              </div>
              <div>
                <div class="demo-chat-title">Confidant AI</div>
                <div class="demo-chat-status">
                  <span class="status-dot"></span>
                  <span id="demo-status">Ready</span>
                </div>
              </div>
            </div>
            <div class="demo-controls">
              <button id="demo-replay" class="demo-control-btn" title="Replay">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"/>
                  <path d="M21 3v5h-5"/>
                  <path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"/>
                  <path d="M3 21v-5h5"/>
                </svg>
              </button>
              <button id="demo-play" class="demo-control-btn" title="Play">
                <svg viewBox="0 0 24 24" fill="currentColor">
                  <polygon points="5 3 19 12 5 21 5 3"/>
                </svg>
              </button>
            </div>
          </div>
          
          <div class="demo-messages" id="demo-messages">
            <div class="demo-welcome">
              <div class="demo-welcome-icon">👋</div>
              <h3>Welcome to Confidant AI Demo</h3>
              <p>Select an example from the left or click Play to see it in action</p>
            </div>
          </div>
          
          <div class="demo-input">
            <input type="text" placeholder="This is a demo - select examples above to see features" disabled>
            <button disabled>
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                <path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/>
              </svg>
            </button>
          </div>
        </div>
      </div>
    `;

    // Event listeners
    document.querySelectorAll('.demo-item').forEach(btn => {
      btn.addEventListener('click', (e) => {
        const demoIdx = parseInt(e.currentTarget.dataset.demo);
        this.loadDemo(demoIdx);
      });
    });

    document.getElementById('demo-play').addEventListener('click', () => this.playDemo());
    document.getElementById('demo-replay').addEventListener('click', () => this.replayDemo());
  }

  loadDemo(index) {
    this.currentDemo = index;
    this.messageIndex = 0;
    this.isPlaying = false;

    // Update active state
    document.querySelectorAll('.demo-item').forEach((btn, idx) => {
      btn.classList.toggle('active', idx === index);
    });

    // Clear messages
    const messagesContainer = document.getElementById('demo-messages');
    messagesContainer.innerHTML = '';

    // Show all messages instantly
    const demo = demoConversations[index];
    demo.messages.forEach(msg => {
      this.addMessage(msg, true);
    });

    this.updateStatus('Ready');
  }

  async playDemo() {
    if (this.isPlaying) return;
    
    this.isPlaying = true;
    this.messageIndex = 0;

    const messagesContainer = document.getElementById('demo-messages');
    messagesContainer.innerHTML = '';

    const demo = demoConversations[this.currentDemo];
    
    for (let i = 0; i < demo.messages.length; i++) {
      if (!this.isPlaying) break;
      
      const msg = demo.messages[i];
      
      if (msg.typing) {
        this.updateStatus('Typing...');
      } else if (msg.role === 'assistant' && !msg.typing) {
        this.updateStatus('Online');
      }

      await this.addMessage(msg, false);
      await this.delay(this.messageDelay);
    }

    this.isPlaying = false;
    this.updateStatus('Ready');
  }

  replayDemo() {
    this.isPlaying = false;
    this.loadDemo(this.currentDemo);
  }

  async addMessage(msg, instant = false) {
    const messagesContainer = document.getElementById('demo-messages');
    const messageDiv = document.createElement('div');
    
    if (msg.role === 'system') {
      messageDiv.className = 'demo-message-system';
      messageDiv.innerHTML = `<span>${msg.text}</span>`;
    } else if (msg.role === 'tool') {
      messageDiv.className = 'demo-message-tool';
      const lines = msg.text.split('\n');
      messageDiv.innerHTML = `
        <div class="tool-header">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
            <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>
          </svg>
          <span>Tool Execution</span>
        </div>
        <div class="tool-content">${lines.map(line => `<div>${line}</div>`).join('')}</div>
      `;
    } else {
      messageDiv.className = `demo-message demo-message-${msg.role}`;
      
      if (msg.role === 'user') {
        messageDiv.innerHTML = `
          <div class="message-content">${this.formatMessage(msg.text)}</div>
          <div class="message-avatar">
            <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
              <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 3c1.66 0 3 1.34 3 3s-1.34 3-3 3-3-1.34-3-3 1.34-3 3-3zm0 14.2c-2.5 0-4.71-1.28-6-3.22.03-1.99 4-3.08 6-3.08 1.99 0 5.97 1.09 6 3.08-1.29 1.94-3.5 3.22-6 3.22z"/>
            </svg>
          </div>
        `;
      } else {
        messageDiv.innerHTML = `
          <div class="message-avatar">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
              <path d="M12 2a5 5 0 1 0 0 10A5 5 0 0 0 12 2z"/>
              <path d="M20 21a8 8 0 1 0-16 0"/>
              <circle cx="18" cy="8" r="3" fill="currentColor" stroke="none"/>
            </svg>
          </div>
          <div class="message-content">${instant ? this.formatMessage(msg.text) : ''}</div>
        `;
      }
    }

    messagesContainer.appendChild(messageDiv);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;

    if (!instant && msg.role === 'assistant' && !msg.typing) {
      await this.typeMessage(messageDiv.querySelector('.message-content'), msg.text);
    }
  }

  async typeMessage(element, text) {
    const formatted = this.formatMessage(text);
    element.innerHTML = '';
    
    // For formatted text, just show it instantly (typing HTML is complex)
    if (formatted.includes('<')) {
      element.innerHTML = formatted;
      return;
    }

    for (let char of text) {
      element.textContent += char;
      await this.delay(this.typingSpeed);
    }
  }

  formatMessage(text) {
    // Format markdown-style text
    return text
      .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
      .replace(/\n/g, '<br>')
      .replace(/• /g, '<span style="color: var(--c-purple-light)">•</span> ');
  }

  updateStatus(status) {
    const statusEl = document.getElementById('demo-status');
    if (statusEl) statusEl.textContent = status;
  }

  delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}

// Initialize demo when DOM is ready
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', () => new ChatDemo());
} else {
  new ChatDemo();
}
