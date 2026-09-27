# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

# Temporary cover and icon, until an artist draws the real ones. Run from the
# repo root with Pillow installed.
from PIL import Image, ImageDraw, ImageFont
T='src/main/resources/assets/beltworks/textures/block/'
tiers=['conveyorbelt','improved_conveyorbelt','express_conveyorbelt','turbo_conveyorbelt']
def make(W,H,out,title_size,lanes_px):
    img=Image.new('RGB',(W,H),(24,26,32))
    tile=lanes_px
    n=len(tiers); gap=tile//4
    total=n*tile+(n-1)*gap; y0=(H-total)//2
    for i,t in enumerate(tiers):
        f=Image.open(f'{T}{t}/frame_00.png').convert('RGBA').rotate(90,expand=True).resize((tile,tile),Image.NEAREST)
        y=y0+i*(tile+gap); off=(i*tile//3)%tile
        for x in range(-off,W,tile): img.paste(f,(x,y),f)
    # dark band behind title
    band=Image.new('RGBA',(W,H),(0,0,0,0)); d=ImageDraw.Draw(band)
    bh=int(title_size*1.6); d.rectangle([0,(H-bh)//2,W,(H+bh)//2],fill=(16,17,22,215))
    img=Image.alpha_composite(img.convert('RGBA'),band)
    d=ImageDraw.Draw(img)
    f1=ImageFont.truetype('/System/Library/Fonts/Supplemental/Impact.ttf',title_size)
    f2=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial Black.ttf',int(title_size*0.62))
    a,w='BELT','works'
    b1=d.textbbox((0,0),a,font=f1); b2=d.textbbox((0,0),w,font=f2)
    w1=b1[2]-b1[0]; w2=b2[2]-b2[0]; h2=b2[3]-b2[1]
    px=int(title_size*0.16); py=int(title_size*0.12); gap=int(title_size*0.1)
    bw=w2+2*px; tot=w1+gap+bw; x=(W-tot)//2
    h1=b1[3]-b1[1]; y=(H-h1)//2-b1[1]; s=max(3,title_size//20)
    d.text((x+s-b1[0],y+s),a,font=f1,fill=(0,0,0))
    d.text((x-b1[0],y),a,font=f1,fill=(245,200,40))
    bx=x+w1+gap; bt=(H-h1)//2; bb=bt+h1
    d.rounded_rectangle([bx+s,bt+s,bx+bw+s,bb+s],radius=px,fill=(0,0,0))
    d.rounded_rectangle([bx,bt,bx+bw,bb],radius=px,fill=(240,240,236))
    ty=bt+(h1-h2)//2-b2[1]
    d.text((bx+px-b2[0],ty),w,font=f2,fill=(24,26,32))
    img.convert('RGB').save(out)
make(1280,640,'publish/beltworks-cover.png',150,112)
make(512,512,'publish/beltworks-icon.png',92,96)
