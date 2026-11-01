/* Fragments remain on the device; this page sends no puzzle data to a server. */
(() => {
  const base = "https://cbulock.github.io/minescape/play/";
  const title = document.getElementById("title");
  const description = document.getElementById("description");
  const open = document.getElementById("open-app");
  const copy = document.getElementById("copy-link");
  const status = document.getElementById("status");
  function readChallenge(fragment) {
    if (fragment.length > 1300) throw new Error("This puzzle link is too long.");
    if (/^d1\.\d{4}-\d{2}-\d{2}$/.test(fragment)) {
      const iso = fragment.slice(3);
      const date = new Date(iso + "T12:00:00");
      const now = new Date();
      const today = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,"0")}-${String(now.getDate()).padStart(2,"0")}`;
      if (!Number.isFinite(date.getTime()) || date.getFullYear() !== Number(iso.slice(0,4)) || date.getMonth()+1 !== Number(iso.slice(5,7)) || date.getDate() !== Number(iso.slice(8,10))) throw new Error("This daily puzzle date is invalid.");
      if (iso > today) throw new Error("This daily puzzle is not available yet. Try the link on its date.");
      return { title: "Your daily puzzle.", description: date.toLocaleDateString(undefined, {dateStyle:"long"}) + " · Intermediate · No-guess. Past puzzles open as practice." };
    }
    if (!/^b1\.[A-Za-z0-9_-]+$/.test(fragment)) throw new Error("This puzzle link is incomplete or uses an unsupported version.");
    const encoded = fragment.slice(3).replace(/-/g,"+").replace(/_/g,"/");
    const bytes = Uint8Array.from(atob(encoded), c => c.charCodeAt(0));
    if (bytes.length < 20 || bytes.length > 638) throw new Error("This puzzle link is damaged.");
    const view = new DataView(bytes.buffer);
    const columns = bytes[0], rows = bytes[1], mines = view.getUint16(2), opening = view.getUint16(4), size = view.getUint16(6);
    if (columns < 5 || columns > 50 || rows < 5 || rows > 50 || mines < 1 || mines > Math.floor(columns*rows*35/100) || opening >= columns*rows || size !== Math.ceil(columns*rows/8) || bytes.length !== 12+size*2) throw new Error("This puzzle link is damaged.");
    let crc = 0xffffffff;
    for (const byte of bytes.subarray(0, bytes.length-4)) {
      crc ^= byte;
      for (let bit=0; bit<8; bit++) crc = (crc>>>1)^((crc&1) ? 0xedb88320 : 0);
    }
    if (((crc^0xffffffff)>>>0) !== view.getUint32(bytes.length-4)) throw new Error("This puzzle link is damaged. Ask for a new copy.");
    let mineCount=0, openCount=0;
    for (let i=0; i<size*8; i++) {
      const mine = (bytes[8+(i>>3)]>>(i%8))&1;
      const revealed = (bytes[8+size+(i>>3)]>>(i%8))&1;
      if ((mine && revealed) || (i>=columns*rows && (mine || revealed))) throw new Error("This puzzle link is damaged.");
      mineCount += mine; openCount += revealed;
    }
    if (mineCount !== mines || !openCount || ((bytes[8+(opening>>3)]>>(opening%8))&1)) throw new Error("This puzzle link is damaged.");
    return { title:"Your next challenge.", description:`${columns} × ${rows} · ${mines} mines. Play your friend's exact board and starting opening with a fresh timer in practice.` };
  }
  function render() {
    open.hidden = copy.hidden = document.getElementById("instructions").hidden = true;
    status.textContent = "";
    document.getElementById("manual-link").hidden = document.getElementById("manual-label").hidden = true;
    const fragment = window.location.hash.slice(1);
    try {
      const challenge = readChallenge(fragment);
      title.textContent = challenge.title;
      description.textContent = challenge.description;
      open.href = "minescape://play/#" + fragment;
      open.hidden = copy.hidden = document.getElementById("instructions").hidden = false;
      copy.onclick = async () => {
        const link = base + "#" + fragment;
        try { await navigator.clipboard.writeText(link); status.textContent = "Puzzle link copied."; }
        catch {
          const field = document.getElementById("manual-link");
          field.value = link; field.hidden = false;
          document.getElementById("manual-label").hidden = false;
          field.focus(); field.select(); status.textContent = "Select and copy the link above.";
        }
      };
    } catch (error) {
      title.textContent = "Let's find your puzzle.";
      description.textContent = error.message;
    }
  }
  window.addEventListener("hashchange", render);
  render();
})();
