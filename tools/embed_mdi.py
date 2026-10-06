import base64
from pathlib import Path

root = Path(__file__).resolve().parents[1]
assets_dir = root / 'assets'
mdi_dir = assets_dir / 'mdi'
css_file = mdi_dir / 'materialdesignicons.min.css'
font_file = mdi_dir / 'materialdesignicons-webfont.woff2'

if font_file.exists() and css_file.exists():
    css_text = css_file.read_text(encoding='utf-8')
    font_b64 = base64.b64encode(font_file.read_bytes()).decode('ascii')

    font_face = '@font-face{font-family:"Material Design Icons";src:url("data:font/woff2;charset=utf-8;base64,' + font_b64 + '") format("woff2");font-weight:normal;font-style:normal}'
    close_brace = css_text.find('}')
    if close_brace != -1:
        final_css = font_face + css_text[close_brace+1:]
        (assets_dir / 'materialdesignicons.min.css').write_text(final_css, encoding='utf-8')
        print("Generated assets/materialdesignicons.min.css! Size:", (assets_dir / 'materialdesignicons.min.css').stat().st_size)
