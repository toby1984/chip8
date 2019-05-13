
.alias v0 = x
.alias v1 = y
.alias v2 = spritePtrHi
.alias v3 = spritePtrLo
.alias v4 = dx
.alias v5 = dy
.alias v6 = spriteNo
.alias v7 = tmp
.alias v8 = minusSix
.alias v9 = ptrLo
.alias v10 = ptrHi
.alias v11 = loopCount

.equ SPRITE_COUNT = 4
.equ STRUCT_SIZE = 6 ; 6 bytes of x/y/dx/dy/ptr data per sprite

.macro neg(reg) {
    LD tmp,reg
    LD reg,0
    SUB reg,tmp   
}

.macro high(value) = (value>>8) & 0xff
.macro low(value) = value & 0xff

.equ HEIGHT = 32
.equ WIDTH = 64

   LD minusSix,0-6

start:
   CALL draw ; draw sprites

   LD tmp,2 ; wait some time
   LD dt,tmp

   CALL draw ; clear sprites

   LD loopCount, SPRITE_COUNT
   LD ptrHi,high(data)
   LD ptrLo,low(data)   
move:
   LD v13,ptrHi
   LD v14,ptrLo
   CALL load_i

   LD dy,[I]
; inc X
   ADD x,dx
   SNE x, WIDTH-8
   JP flip_dx
   SNE x,0
   JP flip_dx
.check_y
   ADD y,dy
   SNE y, HEIGHT-8
   JP flip_dy
   SNE y,0
   JP flip_dy
; persist updated variables  
.persist
   LD v13,ptrHi
   LD v14,ptrLo
   CALL load_i

   LD [I],dy
   
   ADD loopCount,0xff
   SNE loopCount,0
   JP start

   LD v0,STRUCT_SIZE
   ADD ptrLo,v0
   ADD ptrHi,v15 ; VF
   JP move
   
.flip_dy
   neg dy
   JP persist

.flip_dx
   neg dx
   JP check_y   

draw:
   LD loopCount, SPRITE_COUNT
   LD ptrLo,low(data)
   LD ptrHi,high(data)
.loop
   LD v13,ptrHi
   LD v14,ptrLo
   CALL load_i   
   LD dy,[I]
   LD v13,spritePtrHi
   LD v14,spritePtrLo
   CALL load_i
   DRW x,y,8
; increment ptr
   LD tmp, STRUCT_SIZE
   ADD ptrLo,tmp
   ADD ptrHi,v15 ; vf  
; decrement loop count
   ADD loopCount,0xff
   SNE loopCount,0
   RET
   JP loop

; Use self-modifying code
; to load I with arbitray 12-bit
; value in v14 (hi) & v13 (lo)
; SCRATCHED: v0,v13

load_i:  
.alias v13 = hi
.alias v14 = lo
   LD tmp,v0 ; save v0
; prepare high-byte of instruction
   LD v0,hi
   LD hi,0x0f
   AND v0,hi
   LD hi,0xa0
   OR v0,hi
   LD I,insn
   LD [I],v0
; prepare low-byte of instruction
   LD v0,lo
   LD [i],v0
.insn
   .byte 0,0
   LD v0,tmp ; restore v0
   RET

data:
   .byte 3,3 ; x,y
   .word sprite1
   .byte 1,1 ; dx,dy

   .byte 20,20 ; x,y
   .word sprite2
   .byte 0xff,0xff ; dx,dy

   .byte 50,5 ; x,y
   .word sprite3
   .byte 0x01,0xff ; dx,dy

   .byte 2,20 ; x,y
   .word sprite4
   .byte 0xff,0x01 ; dx,dy

   
sprite1:
.byte %11111111
.byte %10000001
.byte %10000001
.byte %10000001
.byte %10000001
.byte %10000001
.byte %10000001
.byte %11111111

sprite2:
.byte %00011000
.byte %00011000
.byte %00011000
.byte %11111111
.byte %00011000
.byte %00011000
.byte %00011000
.byte %00011000

sprite3:
.byte %10000001
.byte %01000010
.byte %00100100
.byte %00011000
.byte %00011000
.byte %00100100
.byte %01000010
.byte %10000001

sprite4:
.byte %11111111
.byte %11111111
.byte %11111111
.byte %11111111
.byte %11111111
.byte %11111111
.byte %11111111
.byte %11111111


