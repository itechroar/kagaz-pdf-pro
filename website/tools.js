(function () {
  // s: a = own tool in the app, c = done with the Convert tool, p = not built yet.
  var A = 'a', C = 'c', P = 'p';
  var NOT_TO = ['Publisher', 'HEIC', 'PUB', 'UDF', 'OFD', 'PS'];
  var NOT_FROM = ['SVG', 'ODS'];
  var CATS = [
    ['Frequently used', [['Merge PDF', A], ['Split PDF', A], ['Compress PDF', A], ['Compress to size (KB / MB)', A], ['Edit PDF', A], ['Sign PDF', A], ['PDF converter', C], ['Images to PDF', C], ['PDF to images', C]]],
    ['Create', [['Create PDF', C], ['Write PDF', P], ['Create PDF with a camera', P], ['Straighten scans', P], ['Webpage to PDF', P], ['HTML file to PDF', C], ['Images to PDF', C], ['Photo collage', P], ['Create card', P], ['Create PDF form', A, 'Form editor'], ['Job application', P], ['Generate QR code', P]]],
    ['Invoices', [['Create invoice', P], ['Create invoice visually', P], ['Create electronic invoice', P], ['PDF invoice to e-invoice', P], ['XML e-invoice to PDF', P], ['Validate e-invoice', P]]],
    ['Edit', [['Edit PDF', A], ['Annotate PDF', A], ['Fill out PDF', A, 'Form editor'], ['Make PDF fillable', A, 'Auto form detection'], ['Add watermark', A], ['Add page numbers', A], ['PDF overlay', A], ['Crop PDF', A], ['Change PDF page size', A], ['Change PDF document information', A], ['PDF info', A]]],
    ['Organize', [['Merge PDF', A], ['Assemble PDF', A, 'Multi tool'], ['Split PDF', A], ['Rearrange PDF pages', A], ['Remove PDF pages', A], ['Extract PDF images', A], ['Rotate PDF pages', A], ['Pages per sheet', A], ['Print PDF', P], ['Create booklet', A], ['Print PDF as poster', P], ['Halve PDF pages', P], ['Bookmark PDF', A, 'Edit table of contents'], ['Copy from PDF', A, 'Extract pages']]],
    ['Optimize & repair', [['Compress PDF', A], ['PDF to greyscale', A, 'Compress option'], ['Invert PDF colors', A], ['Web optimize PDF', A, 'Compress option'], ['PDF OCR', A], ['Repair PDF', A], ['Rasterize PDF', P], ['Flatten PDF', A], ['PDF to PDF/A', C]]],
    ['Security & privacy', [['Protect PDF', A], ['Unlock PDF', A], ['Sign PDF', A], ['Redact PDF', A], ['Remove PDF metadata', A, 'Sanitize'], ['Flatten PDF', A], ['Generate password', P]]],
    ['View & check', [['View as PDF', A], ['Compare PDFs', A], ['Search in PDFs', A, 'In the viewer'], ['Check PDF/A', P], ['Check PDF accessibility', A], ['Verify PDF signatures', A], ['Set PDF viewer preferences', P]]],
    ['Convert to PDF', ['Word', 'PowerPoint', 'Excel', 'Publisher', 'Images', 'JPG', 'PNG', 'WebP', 'HEIC', 'SVG', 'TIFF', 'DOCX', 'PPTX', 'XLSX', 'DOC', 'PPT', 'XLS', 'ODT', 'ODG', 'ODS', 'ODP', 'Text', 'RTF', 'EPUB', 'PS', 'Markdown', 'PUB', 'UDF', 'OFD', 'EML'].map(function (n) { return [n + ' to PDF', NOT_TO.indexOf(n) === -1 ? C : P]; })],
    ['Convert from PDF', ['Word', 'PowerPoint', 'Excel', 'Images', 'JPG', 'PNG', 'SVG', 'TIFF', 'DOCX', 'PPTX', 'XLSX', 'ODT', 'ODS', 'ODP', 'Text', 'RTF', 'EPUB', 'HTML', 'Markdown', 'PDF/A'].map(function (n) { return ['PDF to ' + n, NOT_FROM.indexOf(n) === -1 ? C : P]; })],
    ['Convert images', [['Compress images', P], ['HEIC to JPG', P], ['HEIC to PNG', P], ['WebP to JPG', P], ['WebP to PNG', P], ['SVG to JPG', P], ['SVG to PNG', P]]],
    ['Desktop', [['PDF creator (virtual printer)', P], ['PDF printer', P], ['PDF reader', A, 'Read / View PDF']]]
  ];
  var LABEL = {a: 'Available', c: 'Via Convert', p: 'Not yet'};
  var root = document.getElementById('cats'), q = document.getElementById('q'), count = document.getElementById('count');
  function render() {
    var term = q.value.trim().toLowerCase(), shown = 0;
    root.textContent = '';
    CATS.forEach(function (cat) {
      var items = cat[1].filter(function (it) { return !term || (it[0] + ' ' + (it[2] || '') + ' ' + cat[0]).toLowerCase().indexOf(term) !== -1; });
      if (!items.length) { return; }
      var sec = document.createElement('section'), h = document.createElement('h2'), ul = document.createElement('ul');
      h.textContent = cat[0]; ul.className = 'tools';
      items.forEach(function (it) {
        var li = document.createElement('li'), name = document.createElement('span'), tag = document.createElement('em');
        name.textContent = it[0] + (it[2] ? ' (' + it[2] + ')' : '');
        tag.textContent = LABEL[it[1]]; tag.className = 's-' + it[1];
        li.appendChild(name); li.appendChild(tag); ul.appendChild(li); shown++;
      });
      sec.appendChild(h); sec.appendChild(ul); root.appendChild(sec);
    });
    count.textContent = shown + ' tools' + (term ? ' match' : '');
  }
  q.addEventListener('input', render); render();
})();
