  LD I, sprite
.alias v5 = x
.alias v6 = y

.equ left = 0x05
.equ right = 0x09
.equ up = 0x07
.equ down = 0x08

  LD x,0
  LD y,0
draw:
   DRW x,y,8
read_key:
   LD v0,10 ; wait some time
   LD dt,v0
   LD v0,K
   SNE v0,left
   jp pressed_left ; left
   SNE v0,right
   JP pressed_right ; right
   SNE v0,up
   JP pressed_up ; up
   SNE v0,down 
   JP pressed_down ; down
   JP read_key
pressed_left: 
   DRW x,y,8
   ADD y,0xff
   JP draw
pressed_right:
    DRW x,y,8
    ADD x,1    
    JP draw
pressed_up:
    DRW x,y,8
    ADD x,0xff
    JP draw  
pressed_down:
    DRW x,y,8
    ADD y,0x01
    JP draw
sprite:
.byte %10000001
.byte %01000010
.byte %00100100
.byte %11111111
.byte %11111111
.byte %00100100
.byte %01000010
.byte %10000001