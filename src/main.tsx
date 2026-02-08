import { createRoot } from "react-dom/client";
import App from "./App.tsx";
import "./index.css";

console.log("🚀 Main.tsx loaded");
const rootElement = document.getElementById("root");
console.log("📦 Root element:", rootElement);

if (rootElement) {
  createRoot(rootElement).render(<App />);
  console.log("✅ App rendered");
} else {
  console.error("❌ Root element not found");
}
