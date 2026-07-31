# JobCraft 🚀

**JobCraft** is an intelligent, full-featured Android application designed to streamline job searching, automated CV tailoring, cover letter generation, and application tracking. Built with modern Jetpack Compose and powered by Google Gemini AI, JobCraft helps candidates adapt their resume for every job posting and manage their career search end-to-end.

---

## 🌟 Key Features

### 🔍 1. Smart Job Scraper & Web Search
- **Multi-Board Scraping**: Scrapes job listings directly from major job platforms including **LinkedIn**, **Indeed**, **ZipRecruiter**, **Fuzu**, **BrighterMonday**, or any custom target URL.
- **Direct Application Routing**: Postings direct candidates straight to the official application links on the original job host site rather than generic search pages.
- **Site Selector**: A clean dropdown menu to select target job boards or enter custom search URLs directly.

### 📄 2. AI-Powered CV & Cover Letter Tailoring
- **Gemini AI Integration**: Analyzes job requirements, core keywords, and required skills to automatically customize baseline candidate CVs into highly tailored resumes.
- **Executive Base Template**: Pre-formatted executive resume structure showcasing technical skills, structured experience, education, leadership, and volunteer achievements.
- **Custom Cover Letter Drafting**: Automatically writes compelling, role-specific cover letters aligned with the job description.
- **Interactive AI Chat Assistant**: Refine CV bullet points or request custom modifications on the fly through an inline assistant interface.

### 🖨️ 3. Executive PDF Document Generation
- **Native Android PDF Renderer**: Built-in `PdfDocument` engine renders polished, print-ready PDF resumes and cover letters.
- **Executive Typography & Layout**: Features an executive color palette (#1B4D89 Dark Executive Blue), clean section dividers, bullet formatting with hanging indents, and automatic page wrapping.
- **Instant Export & Share**: Save PDFs directly to local device storage or share via Android system intent.

### 📊 4. Application Tracker & Local Persistence
- **Room Local Database**: Securely logs applied positions, job details, customized CVs, cover letters, deadlines, and notes offline.
- **Application Status & Reminders**: Track application progress across stages (*Saved*, *Applied*, *Interview*, *Offered*, *Rejected*) and set phone notifications/reminders for application deadlines or interview dates.
- **Archive Registry**: Review historical application logs and re-download archived PDF documents at any time.

---

## 🛠️ Architecture & Tech Stack

- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose (Material Design 3)
- **Local Database**: Room Database with KSP
- **AI Integration**: Google Gemini API (`google-genai` REST integration)
- **Document Engine**: Native Android `android.graphics.pdf.PdfDocument`
- **Architecture**: MVVM (Model-View-ViewModel) with Kotlin Coroutines & StateFlow

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio**: Jellyfish | 2023.3.1 or newer
- **Android SDK**: API level 26 (Android 8.0) or higher
- **Gemini API Key**: Obtain an API key from Google AI Studio and configure `GEMINI_API_KEY` in your environment or BuildConfig.

### Installation
1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-username/JobCraft.git
   cd JobCraft
   ```
2. **Open in Android Studio**: Open the root project directory in Android Studio and let Gradle sync dependencies.
3. **Run the Application**: Build and deploy to an Android device or emulator running API level 26+.

---

## 🔒 Security & Admin Access
- Baseline import tools and administrative access retain pre-configured `root` / `root` credentials for quick demonstration and profile setup.
- Candidate data is stored locally on-device using Room local persistence.

---

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.
