/* Render PlantUML files via Kroki to SVG and PNG */
const fs = require('fs');
const path = require('path');
const https = require('https');

const UML_SOURCE_PATH = path.resolve(__dirname, '..', 'public', 'uml', 'coderzclub-classes.puml');
const OUTPUT_DIR = path.resolve(__dirname, '..', 'public', 'uml');

function requestKroki(format, source) {
  const options = {
    method: 'POST',
    hostname: 'kroki.io',
    path: `/plantuml/${format}`,
    headers: {
      'Content-Type': 'text/plain',
      'Content-Length': Buffer.byteLength(source)
    }
  };

  return new Promise((resolve, reject) => {
    const req = https.request(options, (res) => {
      const chunks = [];
      res.on('data', (d) => chunks.push(d));
      res.on('end', () => {
        if (res.statusCode && res.statusCode >= 200 && res.statusCode < 300) {
          resolve(Buffer.concat(chunks));
        } else {
          reject(new Error(`Kroki error ${res.statusCode}: ${Buffer.concat(chunks).toString('utf8')}`));
        }
      });
    });
    req.on('error', reject);
    req.write(source);
    req.end();
  });
}

async function main() {
  if (!fs.existsSync(UML_SOURCE_PATH)) {
    console.error(`Source not found: ${UML_SOURCE_PATH}`);
    process.exit(1);
  }
  const source = fs.readFileSync(UML_SOURCE_PATH, 'utf8');
  if (!fs.existsSync(OUTPUT_DIR)) {
    fs.mkdirSync(OUTPUT_DIR, { recursive: true });
  }

  console.log('Rendering SVG via Kroki...');
  const svg = await requestKroki('svg', source);
  const svgPath = path.join(OUTPUT_DIR, 'coderzclub-classes.svg');
  fs.writeFileSync(svgPath, svg);
  console.log(`Wrote ${svgPath}`);

  console.log('Rendering PNG via Kroki...');
  const png = await requestKroki('png', source);
  const pngPath = path.join(OUTPUT_DIR, 'coderzclub-classes.png');
  fs.writeFileSync(pngPath, png);
  console.log(`Wrote ${pngPath}`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
}); 