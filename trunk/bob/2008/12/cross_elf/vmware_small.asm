  BITS 32
  
                org     0x08048000
  
  ehdr:                                                 ; Elf32_Ehdr
                db      0x7F, "ELF", 1, 1, 1            ;   e_ident
        times 9 db      0
                dw      2                               ;   e_type
                dw      3                               ;   e_machine
                dd      1                               ;   e_version
                dd      _start                          ;   e_entry
                dd      phdr - $$                       ;   e_phoff
                dd      0                               ;   e_shoff
                dd      0                               ;   e_flags
                dw      ehdrsize                        ;   e_ehsize
                dw      phdrsize                        ;   e_phentsize
                dw      1                               ;   e_phnum
                dw      0                               ;   e_shentsize
                dw      0                               ;   e_shnum
                dw      0                               ;   e_shstrndx
  
  ehdrsize      equ     $ - ehdr
  
  phdr:                                                 ; Elf32_Phdr
                dd      1                               ;   p_type
                dd      0                               ;   p_offset
                dd      $$                              ;   p_vaddr
                dd      $$                              ;   p_paddr
                dd      filesize                        ;   p_filesz
                dd      filesize                        ;   p_memsz
                dd      5                               ;   p_flags
                dd      0x1000                          ;   p_align
  
  phdrsize      equ     $ - phdr
  
  _start:
                mov     eax, 1      ; system call 1 (exit)
                mov     ebx, 0      ; default return value of 0
                sldt    edx         ; SLDT to detect VMWare
                cmp     dl, 0       ; if first byte is 0
                je      .L2         ; jump to end (return 0)
                cmp     dh, 0       ; if second byte is 0
                je      .L2         ; jump to end (return 0)
                mov     ebx, 2      ; otherwise, set return value of 2
 .L2:
                push    ebx         ; push return value arg onto stack
                push    eax         ; push syscall number onto stack
                int     0x80        ; syscall interrupt

filesize        equ     $ - $$

