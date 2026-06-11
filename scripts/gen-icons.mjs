// Genera los iconos PWA de Ítaca (fondo verde salvia + "Í" en ecru).
// PNG mínimo escrito a mano (sin dependencias de imagen).
import { deflateSync } from "node:zlib";
import { writeFileSync, mkdirSync } from "node:fs";

const BG = [74, 93, 78]; // --accent
const FG = [246, 244, 239]; // --accent-fg

function crc32(buf) {
  let c = ~0;
  for (let i = 0; i < buf.length; i++) {
    c ^= buf[i];
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

function png(size) {
  const px = (x, y, c) => {
    const o = y * (size * 3 + 1) + 1 + x * 3;
    raw[o] = c[0];
    raw[o + 1] = c[1];
    raw[o + 2] = c[2];
  };
  // scanlines con byte de filtro 0 al inicio de cada fila
  const raw = Buffer.alloc(size * (size * 3 + 1));
  for (let y = 0; y < size; y++) {
    raw[y * (size * 3 + 1)] = 0;
    for (let x = 0; x < size; x++) px(x, y, BG);
  }
  // Glifo "Í": barra vertical centrada + acento + remates serif
  const cx = Math.round(size / 2);
  const barW = Math.round(size * 0.12);
  const top = Math.round(size * 0.3);
  const bot = Math.round(size * 0.72);
  const serif = Math.round(size * 0.16);
  for (let y = top; y < bot; y++)
    for (let x = cx - barW / 2; x < cx + barW / 2; x++) px(Math.round(x), y, FG);
  // remates
  const sH = Math.round(size * 0.035);
  for (const yy of [top, bot - sH])
    for (let y = yy; y < yy + sH; y++)
      for (let x = cx - serif / 2; x < cx + serif / 2; x++) px(Math.round(x), y, FG);
  // acento agudo
  const aY = Math.round(size * 0.2);
  for (let i = 0; i < Math.round(size * 0.09); i++)
    for (let t = 0; t < sH; t++) px(cx - i, aY + i + t, FG);

  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(size, 0);
  ihdr.writeUInt32BE(size, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // color type RGB
  const sig = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  return Buffer.concat([
    sig,
    chunk("IHDR", ihdr),
    chunk("IDAT", deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

mkdirSync("public/icons", { recursive: true });
for (const s of [192, 512]) writeFileSync(`public/icons/icon-${s}.png`, png(s));
writeFileSync("public/icons/apple-touch-icon.png", png(180));
console.log("Iconos generados en public/icons/");
