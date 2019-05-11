.equ SCREEN_SIZE = (64/8)*32

.alias v14 = tmp
.alias v13 = activeScreen ; 0 or 1

;
; ------------ main -------

    CALL initialize   ; initialize screen1 with random values 
mainloop:    
    CALL game_of_life
    CALL draw_screen
    XOR activeScreen,activeScreen
    JP mainloop

game_of_life:
     ; TODO: Implement me
     RET

draw_screen:
     ; TODO: Implement me
     RET

; Initialize screen1 with random data 
initialize:
    LD activeScreen, 0
    LD I,screen1
    LD v2, SCREEN_SIZE/2
.loop
    RND v0,0xff
    RND v1,0xff
    LD [i],v1
    ADD v2,0xff
    SSE v2,0
    JP loop
    RET
       
; Fill memory with zeros
; input: v1 = number of WORDS to clear
; I register: 
clear_memory:
    LD v0,0
    LD v1,0
.loop
    LD [I],v1
    ADD v1,0xff
    SNE v1,0
    RET
    JP loop

; count neighbour pixels
; input: x/y coordinates in v0/v1
; output: number of neighbouring alive cells

; calculate byte offset and bitmas
; to read the cell state at a given (x,y) location
calc_bitoffset:

.alias v0 = x
.alias v1 = y
.alias v2 = byteOffset
.alias v3 = bitMask ; with a 1-bit at the corresponding position
.alias v4 = bitOffset
    
   ; calculate byte offset
    LD tmp,%111111
    AND x, tmp ; make sure X is in rang;e (0..63)
    LD tmp, %11111
    AND y, tmp ; make sure Y is in range (0..31)
    LD byteOffset, x
    SHR byteOffset ; X/8
    SHR byteOffset
    SHR byteOffset
    LD tmp, byteOffset ; tmp = X/8
    ADD byteOffset,y ; byteOffset = (x/8)+y
; calculate bit offset
    SHL tmp
    SHL tmp
    SHL tmp ; tmp = (x>>3)<<3
    LD bitOffset,x
    SUB bitOffset,tmp ; bitOffset = x - (x>>3)<<3
    ADD bitOffset,-7 ; => y = bitOffset = 7 - x - (x>>3)<<3    
    LD bitMask, %10000000
    SNE bitOffset,7 ; if bitOffset != 7
    RET ; bitOffset = 7
.loop
    SHR bitMask
    ADD bitOffset,0xff
    SNE bitOffset,0 ; loop until bitOffset == 0
    RET
    JP loop  

.clearAliases x,y,bitOffset,bitMask  

screen1:
    .reserve SCREEN_SIZE
screen2:
    .reserve SCREEN_SIZE