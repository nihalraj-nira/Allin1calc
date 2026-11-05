"use strict";

/* Allin1Calc: shared script used by every page.
   It builds the menu, switches dark mode, talks to the Java server and keeps the history. */

/* Empty means "the same server that shows this page". If you ever host the frontend somewhere else,
   put the link of the Java server here, for example "https://allin1calc.onrender.com". */
const API_BASE = "";

const PAGES = [
  { group: "Age & Date", items: [["age.html", "Age Calculator"], ["age-difference.html", "Age Difference"], ["date-difference.html", "Date Difference"], ["add-subtract-date.html", "Add / Subtract Date"], ["workdays.html", "Workdays Counter"], ["leap-year.html", "Leap Year Checker"]] },
  { group: "Time", items: [["time-calculator.html", "Time Calculator"], ["hours-worked.html", "Hours Worked"], ["countdown.html", "Countdown"]] },
  { group: "Units", items: [["unit-converter.html", "Unit Converter"]] },
  { group: "Student Tools", items: [["attendance.html", "Attendance Calculator"], ["bunk-planner.html", "Bunk Planner"], ["percentage.html", "Percentage Calculator"], ["average.html", "Average Calculator"], ["cgpa.html", "CGPA to Percentage"]] },
  { group: "More", items: [["history.html", "History"]] },
];

/* ===== Small helpers (also used by the page scripts) ===== */
const $ = (sel, root = document) => root.querySelector(sel);
const pad = (n) => String(n).padStart(2, "0");
const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const todayISO = (offset = 0) => {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};
const nowISO = () => {
  const d = new Date();
  return `${todayISO()}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};
const dmy = (iso) => String(iso).split("-").reverse().join("-");
const num = (n, max = 2) => (n === null || n === undefined || !Number.isFinite(Number(n)) ? "n/a" : Number(n).toLocaleString("en-US", { maximumFractionDigits: max }));
const plural = (n, word) => `${num(n, 0)} ${word}${Number(n) === 1 ? "" : /s$/.test(word) ? "es" : "s"}`;
const ymd = (d) => `${plural(d.years, "year")}, ${plural(d.months, "month")}, ${plural(d.days, "day")}`;
const countdownText = (totalSeconds) => {
  const s = Math.max(0, Math.floor(totalSeconds));
  return `${plural(Math.floor(s / 86400), "day")}, ${plural(Math.floor(s / 3600) % 24, "hour")}, ${plural(Math.floor(s / 60) % 60, "minute")}, ${plural(s % 60, "second")}`;
};

const store = {
  get(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw === null ? fallback : JSON.parse(raw);
    } catch (e) {
      return fallback;
    }
  },
  set(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) { /* storage may be blocked; everything else still works */ }
  },
};

/* ===== Talking to the Java server ===== */
async function api(path, body) {
  const options = body === undefined ? {} : { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
  let res;
  try {
    res = await fetch(API_BASE + path, options);
  } catch (e) {
    throw new Error("Cannot reach the server. Start it with: java -cp out Server");
  }
  let data;
  try {
    data = await res.json();
  } catch (e) {
    throw new Error("Unexpected reply from the server. Open this page through http://localhost:8080 instead of double-clicking the file.");
  }
  if (!res.ok) {
    throw new Error(data.error || "Something went wrong.");
  }
  return data;
}

let unitsData = null;
async function loadUnits() {
  if (!unitsData) {
    unitsData = await api("/api/units");
  }
  return unitsData;
}

/* ===== History (saved only in this browser) ===== */
let history = store.get("allin1calc.history", []);

function addHistory(calc, input, result) {
  history.unshift({ t: Date.now(), calc, input, result });
  history = history.slice(0, 100);
  store.set("allin1calc.history", history);
}

function initHistoryPage() {
  const list = $("#historyList");
  const draw = () => {
    list.innerHTML = history.length
      ? history.map((h) => `<div class="hist-item"><div class="when">${esc(new Date(h.t).toLocaleString())}</div><div class="what">${esc(h.calc)}</div><div>${esc(h.input)}</div><div class="res">${esc(h.result)}</div></div>`).join("")
      : '<div class="empty">No calculations yet. Your results will appear here.</div>';
  };
  draw();
  $("#clearHistBtn").addEventListener("click", () => {
    if (history.length && !confirm("Delete all saved calculations from this browser?")) {
      return;
    }
    history = [];
    store.set("allin1calc.history", history);
    draw();
  });
  $("#exportBtn").addEventListener("click", () => {
    const text = ["Allin1Calc calculation history", "", ...history.map((h) => `${new Date(h.t).toLocaleString()} | ${h.calc} | ${h.input} | ${h.result}`)].join("\n");
    const url = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
    const a = document.createElement("a");
    a.href = url;
    a.download = "allin1calc-history.txt";
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  });
}

/* ===== Calculator pages ===== */
let liveTimer = null;
let lastCopyText = "";

function stopLive() {
  if (liveTimer) {
    clearInterval(liveTimer);
    liveTimer = null;
  }
}

function applyDefaults() {
  document.querySelectorAll("[data-default]").forEach((el) => {
    const d = el.dataset.default;
    el.value = d === "today" ? todayISO() : d === "tomorrow" ? todayISO(1) : d;
  });
  // the server accepts years 1 to 9999 only
  document.querySelectorAll("input[type=date]").forEach((el) => {
    el.setAttribute("min", "0001-01-01");
    el.setAttribute("max", "9999-12-31");
  });
  document.querySelectorAll("[data-max=today]").forEach((el) => el.setAttribute("max", todayISO()));
}

function showError(message) {
  $("#result").innerHTML = `<div class="result error" role="alert">${esc(message)}</div>`;
}

/* res = { headline, caption, badge:{text,tone}, rows:[[label,value]], table:{head,rows}, note, input, live } */
function showResult(res) {
  const rows = (res.rows || []).map(([k, v]) => `<dt>${esc(k)}</dt><dd>${esc(v)}</dd>`).join("");
  const table = res.table
    ? `<table><thead><tr>${res.table.head.map((h) => `<th>${esc(h)}</th>`).join("")}</tr></thead><tbody>${res.table.rows.map((r) => `<tr>${r.map((c) => `<td>${esc(c)}</td>`).join("")}</tr>`).join("")}</tbody></table>`
    : "";
  $("#result").innerHTML = `<div class="result" tabindex="-1">
    <p class="cap">${esc(res.caption || "Result")}</p>
    <p class="headline" id="headline">${esc(res.headline)}</p>
    ${res.badge ? `<span class="badge ${res.badge.tone}">${esc(res.badge.text)}</span>` : ""}
    ${rows ? `<dl class="rows">${rows}</dl>` : ""}${table}
    ${res.note ? `<p class="note">${esc(res.note)}</p>` : ""}
    <button type="button" class="ghost copy" id="copyBtn">Copy result</button>
  </div>`;
  lastCopyText = [res.caption, res.headline + (res.badge ? ` (${res.badge.text})` : ""), ...(res.rows || []).map(([k, v]) => `${k}: ${v}`)].filter(Boolean).join("\n");
  $("#copyBtn").addEventListener("click", copyResult);
  if (res.live) {
    const target = res.live.target;
    liveTimer = setInterval(() => {
      const left = (target - Date.now()) / 1000;
      const el = $("#headline");
      if (!el) {
        stopLive();
      } else if (left <= 0) {
        el.textContent = "The event time has been reached.";
        stopLive();
      } else {
        el.textContent = countdownText(left);
      }
    }, 1000);
  }
}

async function copyResult() {
  const btn = $("#copyBtn");
  try {
    await navigator.clipboard.writeText(lastCopyText);
  } catch (e) {
    const area = document.createElement("textarea");
    area.value = lastCopyText;
    document.body.appendChild(area);
    area.select();
    try { document.execCommand("copy"); } catch (err) { /* nothing more we can do */ }
    area.remove();
  }
  btn.textContent = "Copied!";
  setTimeout(() => { btn.textContent = "Copy result"; }, 1500);
}

/* Every calculator page calls Calc.init({ run, init? }).
   run(values) calls the Java server and returns what to show (see showResult). */
const Calc = {
  async init(config) {
    const form = $("#form");
    const title = $("h2").textContent;
    applyDefaults();
    form.addEventListener("reset", () => {
      setTimeout(applyDefaults, 0);
      $("#result").innerHTML = "";
      stopLive();
    });
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const button = $("button.primary", form);
      button.disabled = true;
      stopLive();
      try {
        const res = await config.run(Object.fromEntries(new FormData(form).entries()));
        showResult(res);
        addHistory(title, res.input || "", res.headline + (res.badge ? ` (${res.badge.text})` : ""));
      } catch (err) {
        showError(err.message);
      } finally {
        button.disabled = false;
      }
    });
    if (config.init) {
      try {
        await config.init(form);
      } catch (err) {
        showError(err.message);
      }
    }
  },
};

/* ===== Header, menu and theme (every page) ===== */
function buildHeader() {
  const here = location.pathname.split("/").pop() || "index.html";
  const menu = PAGES.map((g) => `<div><h3>${esc(g.group)}</h3>${g.items.map(([href, label]) => `<a href="${href}"${href === here ? ' aria-current="page"' : ""}>${esc(label)}</a>`).join("")}</div>`).join("");
  $("#site-header").outerHTML = `
    <header class="top"><div class="wrap top-inner">
      <a class="brand brand-link" href="index.html">
        <img class="logo" src="brand-mark.svg" alt="" width="44" height="44">
        <div><h1>Allin1Calc</h1><p>One App. Every Calculation.</p></div>
      </a>
      <div class="top-actions">
        <button id="menuBtn" class="ghost-light" type="button" aria-expanded="false" aria-controls="menuPanel">Menu</button>
        <button id="themeBtn" class="ghost-light" type="button" aria-label="Switch between light and dark theme">Dark mode</button>
      </div>
    </div></header>
    <div class="wrap"><nav id="menuPanel" class="menu" hidden aria-label="All calculators">${menu}</nav></div>`;
  $("#menuBtn").addEventListener("click", () => {
    const panel = $("#menuPanel");
    panel.hidden = !panel.hidden;
    $("#menuBtn").setAttribute("aria-expanded", String(!panel.hidden));
  });
}

function applyTheme(theme) {
  document.documentElement.dataset.theme = theme;
  $("#themeBtn").textContent = theme === "dark" ? "Light mode" : "Dark mode";
  $("#themeBtn").setAttribute("aria-label", `Switch to ${theme === "dark" ? "light" : "dark"} theme`);
  $("#themeBtn").setAttribute("aria-pressed", String(theme === "dark"));
  store.set("allin1calc.theme", theme);
}

document.addEventListener("DOMContentLoaded", () => {
  buildHeader();
  const preferred = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  applyTheme(store.get("allin1calc.theme", preferred));
  $("#themeBtn").addEventListener("click", () => applyTheme(document.documentElement.dataset.theme === "dark" ? "light" : "dark"));
  if ($("#historyList")) {
    initHistoryPage();
  }
});
