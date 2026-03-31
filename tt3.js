const fs = require('fs');
const data = JSON.parse(fs.readFileSync('latest_scans.json', 'utf8'));
data.items.slice(0, 3).forEach(item => {
    fs.appendFileSync('dump.txt', '--- ' + (item.prediction?.species || 'Unknown') + ' ---\n');
    fs.appendFileSync('dump.txt', (item.payload?.debug?.rawOcrText || '').split('|').join('\n') + '\n');
});
