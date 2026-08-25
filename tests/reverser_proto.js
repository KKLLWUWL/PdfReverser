/* pdf-lib 倒序原型验证：4 类结构 PDF，测速度 + 页序正确性 */
const fs = require('fs');
const path = require('path');
const { PDFDocument } = require(path.join(__dirname, '..', 'node', 'node_modules', 'pdf-lib'));

const dir = path.join(__dirname, 'pdf_in');
const files = fs.readdirSync(dir).filter(f => f.endsWith('.pdf'));

async function reverse(bytes) {
  const doc = await PDFDocument.load(bytes, { ignoreEncryption: true, updateMetadata: false });
  const n = doc.getPageCount();
  if (n <= 1) return { n, skipped: true, out: bytes };
  // 结构级倒序：仅调整页面树顺序，不重渲染/不重新压缩任何内容流
  const n0 = doc.getPageCount();
  const orig = [];
  for (let i = 0; i < n0; i++) orig.push(doc.getPage(i)); // 保持原序摘出
  while (doc.getPageCount() > 0) doc.removePage(0);
  for (let i = orig.length - 1; i >= 0; i--) doc.insertPage(doc.getPageCount(), orig[i]);
  const out = await doc.save({ useObjectStreams: false });
  return { n, skipped: false, out };
}

(async () => {
  for (const f of files) {
    const bytes = fs.readFileSync(path.join(dir, f));
    const t0 = process.hrtime.bigint();
    const r = await reverse(bytes);
    const ms = Number(process.hrtime.bigint() - t0) / 1e6;
    fs.writeFileSync(path.join(__dirname, 'pdf_out_' + f), r.out);
    console.log(`${f}: 原页数=${r.n} 跳过=${r.skipped} 耗时=${ms.toFixed(1)}ms 输出=${(r.out.length/1024).toFixed(1)}KB (输入 ${(bytes.length/1024).toFixed(1)}KB)`);
  }
})();
