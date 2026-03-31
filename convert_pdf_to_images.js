const convert = require('pdf-to-img');
const path = require('path');
const fs = require('fs');

async function convertPDFsToImages() {
  const pdfs = [
    'AGENT_INSTRUCTION_WEBSITE.pdf',
    'WEBSITE_DESIGNBRIEF_JETON_STYLE.pdf',
    'APP_SUMMARY_FOR_WEBSITE.pdf'
  ];

  for (const pdf of pdfs) {
    try {
      const pdfPath = path.join(__dirname, pdf);
      const fileName = path.basename(pdf, '.pdf');
      const outputDir = path.join(__dirname, 'website_instructions_images');
      
      if (!fs.existsSync(outputDir)) {
        fs.mkdirSync(outputDir, { recursive: true });
      }

      console.log(`Converting ${pdf}...`);
      
      const images = await convert({
        input: pdfPath,
        output: {
          dir: outputDir,
          type: 'image/png',
          naming: `${fileName}-page`,
          density: 300,
          quality: 95,
          format: 'png'
        }
      });

      console.log(`✓ ${pdf} converted to ${images.length} PNG images`);
    } catch (error) {
      console.error(`✗ Error converting ${pdf}:`, error.message);
    }
  }
}

convertPDFsToImages();
