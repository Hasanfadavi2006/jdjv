/**
 * ui_spec.h
 * LaserMed Pro X7 — Embedded Touchscreen UI Specification
 *
 * Target:   LPC1788 @ 120 MHz
 * Display:  800 x 480, RGB565
 * Storage:  SD card (FAT32), 8.3 filenames
 * Touch:    4-wire resistive, rectangular zones
 */

#ifndef UI_SPEC_H
#define UI_SPEC_H

#include <stdint.h>

/* ------------------------------------------------------------------ */
/*  Display constants                                                   */
/* ------------------------------------------------------------------ */

#define DISP_W          800
#define DISP_H          480
#define PAGE_COUNT      4
#define MAX_ELEMENTS    64    /* per page */
#define IMG_FILENAME_LEN 12  /* 8.3 + NUL */
#define LABEL_LEN       48

/* ------------------------------------------------------------------ */
/*  RGB565 colour palette                                               */
/* ------------------------------------------------------------------ */

#define COL_BG           ((uint16_t)0x0884)   /* 0x0D1220 */
#define COL_CARD_BG      ((uint16_t)0x10E6)   /* 0x171E30 */
#define COL_CARD_BORDER  ((uint16_t)0x1965)   /* 0x152038 */
#define COL_ACCENT       ((uint16_t)0x05DF)   /* 0x00B8FF */
#define COL_RED          ((uint16_t)0xF9C7)   /* 0xFF3838 */
#define COL_GREEN        ((uint16_t)0x2730)   /* 0x26E580 */
#define COL_ORANGE       ((uint16_t)0xFCC3)   /* 0xFF9919 */
#define COL_PURPLE       ((uint16_t)0x9A5F)   /* 0x9949FF */
#define COL_WHITE        ((uint16_t)0xFFFF)
#define COL_GRAY         ((uint16_t)0x84B6)   /* 0x8094B2 */
#define COL_WARN_BG      ((uint16_t)0x8200)   /* orange dim */
#define COL_RED_DIM      ((uint16_t)0x2000)   /* red dim bg */
#define COL_GREEN_DIM    ((uint16_t)0x0480)   /* green dim bg */
#define COL_TRANSPARENT  ((uint16_t)0x0000)

/* ------------------------------------------------------------------ */
/*  Font IDs  (bitmaps embedded in firmware flash)                     */
/* ------------------------------------------------------------------ */

#define FONT_12   0   /* Inter 12 px — body / labels */
#define FONT_16   1   /* Inter 16 px — sub-headings  */
#define FONT_24   2   /* Inter 24 px — values        */
#define FONT_40   3   /* Inter 40 px — big numbers   */

/* ------------------------------------------------------------------ */
/*  Image indices  (files on SD card, 16-bit BMP RGB565)               */
/* ------------------------------------------------------------------ */

typedef enum {
    IMG_NONE        = 255,
    IMG_LOGO        = 0,   /* LOGO.BMP      34×34  */
    IMG_DOT_GREEN   = 1,   /* DOT_G.BMP     14×14  */
    IMG_DOT_RED     = 2,   /* DOT_R.BMP     14×14  */
    IMG_DOT_ORANGE  = 3,   /* DOT_O.BMP     14×14  */
    IMG_PWR_ON      = 4,   /* PWR_ON.BMP    72×72  */
    IMG_PWR_OFF     = 5,   /* PWR_OFF.BMP   72×72  */
    IMG_BTN_MINUS   = 6,   /* BTN_M.BMP     44×44  */
    IMG_BTN_PLUS    = 7,   /* BTN_P.BMP     44×44  */
    IMG_BTN_PAUSE   = 8,   /* BTN_PS.BMP   144×46  */
    IMG_BTN_STOP    = 9,   /* BTN_ST.BMP   144×46  */
    IMG_BTN_START   = 10,  /* BTN_SR.BMP   296×66  */
    IMG_BTN_ESTOP   = 11,  /* BTN_ES.BMP   136×136 */
    IMG_MODE_CW     = 12,  /* MD_CW.BMP     80×146 */
    IMG_MODE_CW_A   = 13,  /* MD_CWA.BMP    80×146 */
    IMG_MODE_PW     = 14,  /* MD_PW.BMP     80×146 */
    IMG_MODE_PW_A   = 15,  /* MD_PWA.BMP    80×146 */
    IMG_MODE_SP     = 16,  /* MD_SP.BMP     80×146 */
    IMG_MODE_SP_A   = 17,  /* MD_SPA.BMP    80×146 */
    IMG_MODE_FR     = 18,  /* MD_FR.BMP     80×146 */
    IMG_MODE_FR_A   = 19,  /* MD_FRA.BMP    80×146 */
    IMG_NAV_DASH    = 20,  /* NAV_D.BMP    128×72  */
    IMG_NAV_DASH_A  = 21,  /* NAV_DA.BMP   128×72  */
    IMG_NAV_TREAT   = 22,  /* NAV_T.BMP    128×72  */
    IMG_NAV_TREAT_A = 23,  /* NAV_TA.BMP   128×72  */
    IMG_NAV_SAFE    = 24,  /* NAV_S.BMP    128×72  */
    IMG_NAV_SAFE_A  = 25,  /* NAV_SA.BMP   128×72  */
    IMG_NAV_SET     = 26,  /* NAV_G.BMP    128×72  */
    IMG_NAV_SET_A   = 27,  /* NAV_GA.BMP   128×72  */
    IMG_SAFE_OK     = 28,  /* SAFE_OK.BMP  222×120 */
    IMG_SAFE_ERR    = 29,  /* SAFE_ER.BMP  222×120 */
    IMG_SAFE_WARN   = 30,  /* SAFE_WN.BMP  222×120 */
    IMG_COUNT       = 31
} ImgIdx;

/* filename table — index matches ImgIdx */
extern const char* const g_img_files[IMG_COUNT];

/* ------------------------------------------------------------------ */
/*  Element types                                                       */
/* ------------------------------------------------------------------ */

typedef enum {
    ELEM_RECT        = 0,  /* filled rectangle, no image, no touch     */
    ELEM_IMAGE       = 1,  /* blit BMP from SD card                    */
    ELEM_BUTTON      = 2,  /* touchable; optional image + label on top */
    ELEM_TEXT        = 3,  /* firmware-rendered text string            */
    ELEM_INDICATOR   = 4,  /* small status dot (image)                 */
    ELEM_PROGRESS_BAR= 5,  /* dynamic fill; label = pct, param = max_w */
} ElemType;

/* ------------------------------------------------------------------ */
/*  Action IDs                                                          */
/* ------------------------------------------------------------------ */

typedef enum {
    ACTION_NONE              = 0,

    /* Navigation */
    ACTION_NAV_PAGE          = 1,   /* next_page field = target page */

    /* Power */
    ACTION_POWER_TOGGLE      = 2,

    /* Intensity control */
    ACTION_INTENSITY_UP_1    = 3,
    ACTION_INTENSITY_DOWN_1  = 4,
    ACTION_INTENSITY_UP_10   = 5,
    ACTION_INTENSITY_DOWN_10 = 6,

    /* Mode selection — param = mode index (0=CW,1=PW,2=SP,3=FR) */
    ACTION_MODE_SELECT       = 7,

    /* Timer */
    ACTION_TIMER_START       = 8,
    ACTION_TIMER_PAUSE       = 9,
    ACTION_TIMER_STOP        = 10,
    ACTION_TIMER_RESET       = 11,

    /* Pulse parameters */
    ACTION_FREQ_UP           = 12,
    ACTION_FREQ_DOWN         = 13,
    ACTION_PULSEWIDTH_UP     = 14,
    ACTION_PULSEWIDTH_DOWN   = 15,
    ACTION_DUTYCYCLE_UP      = 16,
    ACTION_DUTYCYCLE_DOWN    = 17,
    ACTION_PEAKPOWER_UP      = 18,
    ACTION_PEAKPOWER_DOWN    = 19,

    /* Safety */
    ACTION_EMERGENCY_STOP    = 20,

    /* Settings */
    ACTION_CALIBRATE         = 21,
    ACTION_BRIGHTNESS_UP     = 22,
    ACTION_BRIGHTNESS_DOWN   = 23,
} ActionID;

/* ------------------------------------------------------------------ */
/*  Core data structures                                                */
/* ------------------------------------------------------------------ */

/* Axis-aligned rectangle (screen coordinates) */
typedef struct {
    uint16_t x;
    uint16_t y;
    uint16_t w;
    uint16_t h;
} UIRect;

/*
 * UIElement — 64 bytes total (fits cleanly in cache lines)
 *
 * For ELEM_PROGRESS_BAR:
 *   label  = current percentage string (e.g. "75")
 *   param  = total pixel width of track (for fill calculation)
 *   fg     = fill colour
 *   bg     = track colour
 *
 * For ELEM_BUTTON:
 *   img_idx = image blitted first; label drawn centred on top
 *   action  = fired on touch release inside touch zone
 *   param   = optional extra argument (e.g. mode index)
 *
 * For ELEM_TEXT / ELEM_RECT:
 *   touch.w == 0 means non-interactive (skip hit-test)
 */
typedef struct {
    /* Visual geometry */
    uint16_t  x;
    uint16_t  y;
    uint16_t  w;
    uint16_t  h;

    /* Touch zone (may differ from visual bounds; 0,0,0,0 = no touch) */
    UIRect    touch;

    /* Appearance */
    uint16_t  fg;          /* foreground / text colour (RGB565)  */
    uint16_t  bg;          /* background / fill colour (RGB565)  */
    uint8_t   img_idx;     /* ImgIdx — 255 = no image            */
    uint8_t   font;        /* FONT_12 … FONT_40                  */
    uint8_t   type;        /* ElemType                           */

    /* Behaviour */
    uint8_t   action;      /* ActionID                           */
    uint8_t   next_page;   /* target page for ACTION_NAV_PAGE    */
    uint8_t   param;       /* extra argument for action          */

    /* State flags */
    uint8_t   enabled;     /* 1 = interactive, 0 = display only  */
    uint8_t   visible;     /* 1 = render this element            */

    /* Text content */
    char      label[LABEL_LEN];
} UIElement;               /* sizeof = 64 + LABEL_LEN = 112 bytes */

/* One full screen page */
typedef struct {
    uint8_t         page_id;
    uint16_t        bg_color;
    uint8_t         elem_count;
    const char*     name;
    const UIElement* elements;
} UIPage;

/* Root application descriptor */
typedef struct {
    uint8_t        page_count;
    const UIPage*  pages;
} UIApp;

/* ------------------------------------------------------------------ */
/*  Global application descriptor (defined in ui_data.c)              */
/* ------------------------------------------------------------------ */

extern const UIApp g_app;

/* ------------------------------------------------------------------ */
/*  Page accessors                                                      */
/* ------------------------------------------------------------------ */

static inline const UIPage* UI_GetPage(uint8_t page_id)
{
    if (page_id >= g_app.page_count) return 0;
    return &g_app.pages[page_id];
}

/* ------------------------------------------------------------------ */
/*  Hit-test helper                                                     */
/* ------------------------------------------------------------------ */

static inline uint8_t UI_HitTest(const UIElement* e, uint16_t px, uint16_t py)
{
    if (!e->enabled || e->touch.w == 0 || e->touch.h == 0) return 0;
    return (px >= e->touch.x && px < (e->touch.x + e->touch.w) &&
            py >= e->touch.y && py < (e->touch.y + e->touch.h));
}

/*
 * Dispatch a touch event; returns ACTION_NONE if no element hit.
 * Caller receives the matching element pointer in *hit_out (may be NULL).
 */
ActionID UI_Dispatch(const UIPage* page, uint16_t px, uint16_t py,
                     const UIElement** hit_out);

/* ------------------------------------------------------------------ */
/*  Render callback interface (implement in your LCD driver layer)     */
/* ------------------------------------------------------------------ */

void UI_RenderPage(const UIPage* page);
void UI_RenderElement(const UIElement* e);

/* progress-bar fill width from percentage [0-100] */
static inline uint16_t UI_ProgressFill(const UIElement* e, uint8_t pct)
{
    return (uint16_t)((uint32_t)e->param * pct / 100u);
}

#endif /* UI_SPEC_H */
