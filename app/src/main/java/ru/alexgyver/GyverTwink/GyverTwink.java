package ru.alexgyver.GyverTwink;

import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import processing.video.*; 
import hypermedia.net.*; 
import ketai.camera.*; 
import ketai.net.*; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class GyverTwink extends PApplet {

// Исходник приложения GyverTwink
// Написано на коленке, возможно позже переделаю =(
// v1.0 beta
// v1.1 release
// v1.2 - калибровка больше 255, автоматический масштаб интерфейса, поля ввода подвинул наверх, оптимизация от TheAirBlow 
// v1.3 - опять фиксы масштаба
// v1.6 - починил связь с гирляндой
// v1.7 - порядок в меню, ОПЯТЬ ПОЧИНИЛ СВЯЗЬ

// ============== ВАЖНО! ===============
// Установить библиотеки из менеджера библиотек:
// (Набросок/Импортировать библиотеку/Добавить библиотеку)
// - Video
// - Ketai

// Установить библиотеки вручную:
// (в documents/processing/libraries)
// - http://ubaa.net/shared/processing/udp/ - download Processing library

// Android/Sketch Permissions установлены
// - CAMERA
// - INTERNET
// - READ_EXTERNAL_STORAGE

// ============== НАСТРОЙКИ ===============
// true - Android режим, false - PC режим
private static final boolean androidMode = true;

// для PC режима раскомментируй две строки ниже. Для Android - закомментируй
//public void openKeyboard() {}
//public void closeKeyboard() {}

// чтобы сбилдить под Android - нужно установить Android mode
// встроенный билдер собирает под SDK версии 29
// я собирал проект в Android Studio под target 32 версии

// масштаб интерфейса
float androidScale/* = 2.8*/;
float pcScale = 1.3f;

// ============== LIBRARIES ===============




KetaiCamera Acam;
Capture Wcam;
UDP udp;

// ============== VARIABLES ================
int X = 60;     // размер сетки по X (задан вручную)
int Y;          // размер сетки по Y
int maxX, maxY; // точка максимума в координатах сетки
int size;       // размер "пикселя"
int[][] brMap;  // карта яркости 
PImage frame, ring;
boolean camReady = false;
boolean camStart = false;
String brIP, curIP;
int port = 8888;
boolean searchF, found = false;
byte parseMode = 0;
int actionTmr;
StringList ips = new StringList();

boolean calibF = false;
int calibCount = 0;
int WW, W;
int offs = 30;
String[] file;

// ============== ПРОГРАММА ===============
public void settings() {
  if (!androidMode) size(600, 900);
  smooth(8);
}

public void setup() {
  androidScale = width/400.0f;
  offs = width / 25;
  if (androidMode) W = width/2;
  else W = 300;      
  WW = width-W-offs;

  file = loadStrings("subnet.txt");
  if (file == null) {
    println("Subnet text file is empty");
    file = new String[1];
    file[0] = "255.255.255.0";
    saveStrings("subnet.txt", file);
  }
  subnet.text = file[0];

  if (androidMode) uiSetScale(androidScale);
  else uiSetScale(pcScale);

  udp = new UDP(this);
  udp.listen(true);
  startSearch();
}

public void draw() {
  if (searchF) {
    if (millis() - actionTmr > 800) {
      searchF = false;
      if (ips.size() == 0) ips.append("not found");
      else {
        found = true;
        requestCfg();
      }
    }
  } else ui();

  // Калибровка
  if (calibF) {
    if (millis() - actionTmr > 400) {
      actionTmr = millis();
      if (calibCount == 0) makeMap(0);
      if (calibCount > PApplet.parseInt(leds.text)) {
        calibF = false;
        sendData(new int[] {3, 2, calibCount/100, calibCount%100, maxX, maxY});
        calibCount = 0;
        return;
      }
      sendData(new int[] {3, 1, calibCount/100, calibCount%100, maxX, maxY});
      calibCount++;
    }
  }
}
// найти разницу
public void findMax() {
  int max = 0, maxi = 0;
  for (int i = 0; i < brMap.length; i++) {
    brMap[i][2] = (brMap[i][1] - brMap[i][0]);
    brMap[i][2] = max(brMap[i][2], 0);
    if (max < brMap[i][2]) {
      max = brMap[i][2];
      maxi = i;
    }
  }
  maxX = maxi % X;
  maxY = maxi / X;

  PGraphics buf = createGraphics(frame.width, frame.height);
  buf.beginDraw();
  buf.stroke(0xFFFF0000);
  buf.strokeWeight(2);
  buf.noFill();
  buf.circle(maxX*size+size/2, maxY*size+size/2, 30);
  buf.endDraw();
  ring = buf.get();
}

// создать карту яркости (0 базовая, 1 текущая, 2 разностная)
public void makeMap(int i) {
  for (int y = 0; y < Y; y++) {
    for (int x = 0; x < X; x++) {
      int sum = 0;
      for (int yy = 0; yy < size; yy++) {
        for (int xx = 0; xx < size; xx++) {
          int pos = (y*size + yy) * frame.width + (x*size + xx);
          int col = frame.pixels[pos];
          //sum += col >> 16 & 0xFF;
          sum += (col & 0xFF) / 3 + (col >> 8 & 0xFF) / 3 + (col >> 16 & 0xFF) / 3;
        }
      }
      sum /= size * size;
      brMap[y*X + x][i] = sum;
    }
  }
}

// вывести карту яркости (0 базовая, 1 текущая, 2 разностная)
public PGraphics drawMap(int m) {
  PGraphics buf = createGraphics(frame.width, frame.height);
  buf.beginDraw();
  buf.noStroke();
  for (int y = 0; y < Y; y++) {
    for (int x = 0; x < X; x++) {
      int col = brMap[y*X + x][m];
      buf.fill(col);
      buf.rect(x*size, y*size, size, size);
    }
  }
  buf.endDraw();
  return buf;
}
byte curTab = 0;
TextInput leds = new TextInput();
TextInput subnet = new TextInput();
DropDown dropIP = new DropDown();
DropDown dropEff = new DropDown();
Toggle power = new Toggle();
Toggle offT = new Toggle();
Toggle auto = new Toggle();
Toggle rnd = new Toggle();
Slider bri = new Slider();
Slider prd = new Slider();
Slider offS = new Slider();
Toggle fav = new Toggle();
Slider scl = new Slider();
Slider spd = new Slider();

String[] effs = {
  "0. Party_grad", 
  "1. Raibow_grad", 
  "2. Stripe_grad", 
  "3. Sunset_grad", 
  "4. Pepsi_grad", 
  "5. Warm_grad", 
  "6. Cold_grad", 
  "7. Hot_grad", 
  "8. Pink_grad", 
  "9. Cyber_grad", 
  "10. RedWhite_grad", 
  "11. Party_noise", 
  "12. Raibow_noise", 
  "13. Stripe_noise", 
  "14. Sunset_noise", 
  "15. Pepsi_noise", 
  "16. Warm_noise", 
  "17. Cold_noise", 
  "18. Hot_noise", 
  "19. Pink_noise", 
  "20. Cyber_noise", 
  "21. RedWhite_noise", 
};

public void ui() {
  uiFill();
  // ====== TABS =======
  int w = width / 3;
  int h = w / 2;
  int y = height - h;

  if (IconButton("wrench", 0, y, w, h, curTab == 0)) switchCfg();
  if (IconButton("adjust", w*1, y, w, h, curTab == 1)) switchEffects();
  if (IconButton("camera", w*2, y, w, h, curTab == 2)) switchCalib();

  if (curTab == 0) cfgTab();
  if (curTab == 1) effTab();
  if (curTab == 2) calibTab();
}

public void cfgTab() {
  uiGlobalX(offs);
  uiResetStep(20);
  LabelCenter("GyverTwink", 20);
  Divider(width-offs*2);

  Label("Subnet:", 15);
  Label("Connection:", 15);
  if (found) {
    Divider(width-offs*2);
    Label("LED amount:", 15);
    Label("Power:", 15);
    Label("Brightness:", 15);
    Divider(width-offs*2);
    Label("Off timer:", 15);
    Label("Turn off in [1-240m]:", 15);
    Divider(width-offs*2);
    Label("Switch effect:", 15);
    Label("Auto:", 15);
    Label("Random:", 15);
    Label("Period [1-10m]:", 15);
  }

  uiResetStep(20);
  uiStep();
  uiStep();

  if (found) { 
    uiStep();
    uiStep();
    uiStep();
    if (leds.show(WW, uiStep(), W) && androidMode) openKeyboard();
    if (leds.done()) {
      if (androidMode) closeKeyboard();
      int am = PApplet.parseInt(leds.text);
      sendData(new int[] {2, 0, am/100, am % 100});
    }
    if (power.show(WW, uiStep())) sendData(new int[] {2, 1, PApplet.parseInt(power.value)});
    if (bri.show(0, 255, WW, uiStep(), W)) sendData(new int[] {2, 2, PApplet.parseInt(bri.value)});
    uiStep();
    if (offT.show(WW, uiStep())) sendData(new int[] {2, 7, PApplet.parseInt(offT.value)});
    if (offS.show(0, 250, WW, uiStep(), W)) sendData(new int[] {2, 8, PApplet.parseInt(offS.value)});
    uiStep();
    if (Button("Next effect", WW, uiStep(), W)) sendData(new int[] {2, 6});
    if (auto.show(WW, uiStep())) sendData(new int[] {2, 3, PApplet.parseInt(auto.value)});
    if (rnd.show(WW, uiStep())) sendData(new int[] {2, 4, PApplet.parseInt(rnd.value)});
    if (prd.show(1, 10, WW, uiStep(), W)) sendData(new int[] {2, 5, PApplet.parseInt(prd.value)});
  }

  uiResetStep(20);
  uiStep();
  uiStep();
  if (subnet.show(WW, uiStep(), W) && androidMode) openKeyboard();
  if (subnet.done()) {
    if (androidMode) closeKeyboard();
    file[0] = subnet.text;
    saveStrings("subnet.txt", file);
  }
  if (dropIP.show(ips.array(), WW, uiStep(), W-s_height)) {
    curIP = ips.get(dropIP.getSelected());
    requestCfg();
  }
  if (IconButton("sync", WW + W-s_height, uiPrevStep())) startSearch();
}

public void effTab() {
  uiGlobalX(offs);
  uiResetStep(50);
  uiGlobalX(offs);
  if (found) {
    Label("Effect:", 15);
    Label("Favorite:", 15);
    Label("Scale:", 15);
    Label("Speed:", 15);

    uiResetStep(60);
    uiStep();
    if (fav.show(WW, uiStep())) sendData(new int[] {4, 1, PApplet.parseInt(fav.value)});
    if (scl.show(0, 255, WW, uiStep(), W)) sendData(new int[] {4, 2, PApplet.parseInt(scl.value)});
    if (spd.show(0, 255, WW, uiStep(), W)) sendData(new int[] {4, 3, PApplet.parseInt(spd.value)});

    uiResetStep(50);

    if (androidMode) uiSetScale(androidScale*0.8f);
    else uiSetScale(pcScale*0.7f);
    if (dropEff.show(effs, WW, uiStep(), W-s_height)) {
      sendData(new int[] {4, 0, dropEff.selected});
      parseMode = 4;
    }
    if (androidMode) uiSetScale(androidScale);
    else uiSetScale(pcScale);
  } else Label("No devices detected!", 15);
}

public void calibTab() { 
  if (found) {
    // Камера не стартовала в PC режиме
    if (!androidMode && Wcam == null) return;

    if (camReady) {
      camReady = false;
      readCam();
      makeMap(1);
      findMax();
    }

    PImage frameScaled = frame.copy();
    frameScaled.resize(0, height*4/5);
    image(frameScaled, (width-frameScaled.width)/2, 0);
    if (calibF) {
      frameScaled = ring.copy();
      frameScaled.resize(0, height*4/5);
      image(frameScaled, (width-frameScaled.width)/2, 0);
    }
    //image(frame, (width-frame.width)/2, 0);
    //if (calibF) image(ring, (width-ring.width)/2, 0);

    uiResetStep(height - width/6 - 2*_step_y);
    uiResetX(0);
    uiGlobalX(0);

    if (Button("Start")) {
      calibF = true;
      sendData(new int[] {3, 0});
      calibCount = 0;
      actionTmr = millis() + 2000;
    }

    Label(str(calibCount*100/(PApplet.parseInt(leds.text)+1))+'%', 15, uiPrevX()+15, uiPrevStep());
    if (Button("Stop")) {
      calibF = false;
      sendData(new int[] {3, 2});
      calibCount = 0;
    }
  } else {
    uiGlobalX(offs);
    uiResetStep(50);
    uiGlobalX(offs);
    Label("No devices detected!", 15);
  }
}

public void switchCfg() {
  curTab = 0;
  sendData(new int[] {2, 7});
  stopCam();
}
public void switchEffects() {
  curTab = 1;
  stopCam();
  sendData(new int[] {4, 0, dropEff.selected});
  parseMode = 4;
}
public void switchCalib() {
  curTab = 2;
  if (found) startCam();
}

public void initCam() {
  if (androidMode) {
    Acam = new KetaiCamera(this, 1280, 720, 30);
    frame = createImage(Acam.height, Acam.width, RGB);
  } else {
    int tmr = millis();
    while (Wcam == null) {
      String[] cameras = Capture.list();
      if (cameras.length != 0) Wcam = new Capture(this, cameras[0]);
      if (millis() - tmr > 5000) {
        println("fuck shit no camera");
        exit();
        return;
      }
    }
    frame = createImage(Wcam.height, Wcam.width, RGB);
  }

  size = frame.width / X;
  Y = frame.height / size;
  brMap = new int[X * Y][3];
  ring = frame.copy();
}

public void startCam() {
  if (Acam == null || Wcam == null) initCam();
  if (!camStart) {
    if (androidMode) Acam.start();
    else if (Wcam != null) Wcam.start();
  }
  camStart = true;
}

public void stopCam() {
  if (camStart) {
    if (androidMode) Acam.stop();
    else Wcam.stop();
  }
  camStart = false;
}

public void readCam() {
  PImage buf;
  if (androidMode) buf = Acam;
  else buf = Wcam;

  int am = frame.height * frame.width;
  for (int i = 0; i < am; i++) {
    frame.pixels[(i % frame.height) * frame.width + (am - i) / frame.height] = buf.pixels[i];
  }
  frame.updatePixels();
}

public void captureEvent(Capture Wcam) {
  Wcam.read();
  camReady = true;
}

public void onCameraPreviewEvent() {
  Acam.read();
  camReady = true;
}
public void mousePressed() {
  pressHandler();
}

public void mouseReleased() {
  releaseHandler();
}

public void keyPressed() {
  keyHandler();
}
/*---------------------------------------
 UI Components of Cards_UI for Processing
 author: Lucas Cassiano - cassiano@mit.edu
 
 version: 2.0 by AlexGyver
 мне не нравится - потом сделаю свою либу 
 */
// Function list (2.0): https://github.com/GyverLibs/cards_ui
// Old demo: https://web.media.mit.edu/~cassiano/projects/cards_ui/
// Icon-images: https://iconsplace.com/
// Fontawesome Icons: https://fontawesome.com/download

//Colors
private int c_very_dark = color(36, 37, 46);
private int c_dark = color(29, 33, 44);
private int c_mid = color(44, 58, 71);
private int c_light= color(51, 64, 80);

private int c_primary= color(33, 115, 139);
private int c_hover = color(32, 155, 160);

private int c_text_color = color(255);

//Click Options
private boolean clicked = false;
private boolean canClick = true;

//For text Input/Edit
String bufferText = null;
boolean doneText = false;

//Default sizes
private int s_big = 200;
private int s_height = 30;
private int s_med = 100;
private int s_small = 50;
private int s_stroke = 7;

//For Cards
int card_h = 0;
int card_w = 0;
private int card_x = 0;
private int card_y = 0;

private int medFontSize = 15;
private int smallFontSize = 15;
private int largeFontSize = 25;

private int d_medFontSize = 15;
private int d_smallFontSize = 15;
private int d_largeFontSize = 25;

private int d_big = 200;
private int d_height = 30;
private int d_med = 100;
private int d_small = 50;
private int d_stroke = 5;

// pos
private int _pos_y = 0;
private int _step_y = s_height+6;
private int _prevX = 0;
private float _ui_scale = 1.0f;
private boolean _drop_open = false;
private int _x_offs = 0;
private boolean _item_changed = false;

// ====================================== STEP =======================================
public void uiResetStep(int y) {
  _pos_y = y;
}
public int uiStep() {
  _pos_y += _step_y;
  return _pos_y - _step_y;
}
public int uiPrevStep() {
  return _pos_y - _step_y;
}
public void uiResetX(int x) {
  _prevX = x;
}
public int uiPrevX() {
  return _prevX;
}
public void uiGlobalX(int x) {
  _x_offs = x;
}
public boolean uiChanged() {
  if (_item_changed) {
    _item_changed = false;
    return true;
  }
  return false;
}

// ====================================== SETTINGS =======================================
public void uiSetScale(float scale) {
  _ui_scale = scale;
  s_big = PApplet.parseInt(d_big * scale);
  s_height = PApplet.parseInt(d_height * scale);
  s_med = PApplet.parseInt(d_med * scale);
  s_small = PApplet.parseInt(d_small * scale);
  s_stroke = PApplet.parseInt(d_stroke * scale);
  medFontSize = PApplet.parseInt(d_medFontSize * scale);
  smallFontSize = PApplet.parseInt(d_smallFontSize * scale);
  largeFontSize = PApplet.parseInt(d_largeFontSize * scale);
  _step_y = PApplet.parseInt((d_height+6) * scale);
}
public void uiTextSize(int size) {
  medFontSize = size;
}
public void inputTextSize(int size) {
  smallFontSize = size;
}
public void tooltipTextSize(int size) {
  largeFontSize = size;
}

public void uiDark() {
  c_very_dark = color(36, 37, 46);
  c_dark = color(29, 33, 44);
  c_mid = color(44, 58, 71);
  c_light = color(51, 64, 80);
  c_hover = color(32, 155, 160);
}

public void uiLight() {
  c_very_dark = color(100);
  c_dark = color(150);
  c_mid = color(200);
  c_light = color(250);
  c_hover = color(32, 155, 160);
  c_text_color = color(10);
}

private void EditText(String txt) {
  bufferText = txt;
}

// ====================================== MISC =======================================
public void uiFill() {
  background(c_dark);
}

// ====================================== LABEL =======================================
public void LabelBase(String text, int size, int x, int y, boolean center) {
  y += _step_y/2;
  fill(c_text_color);
  textSize(PApplet.parseInt(size*_ui_scale));
  if (center) textAlign(CENTER, CENTER);
  else textAlign(LEFT, CENTER);
  text(text, x, y);
}

public void LabelCenter(String text, int size, int x, int y) {
  LabelBase(text, size, x, y, true);
}
public void LabelCenter(String text, int size, int x) {
  LabelBase(text, size, x, uiStep(), true);
}
public void LabelCenter(String text, int size) {
  LabelBase(text, size, width/2, uiStep(), true);
}
public void Label(String text, int size, int x, int y) {
  LabelBase(text, size, x, y, false);
}
public void Label(String text, int size, int x) {
  LabelBase(text, size, x, uiStep(), false);
}
public void Label(String text, int size) {
  int x;
  if (_x_offs != 0) x = _x_offs;
  else x = width/2;
  LabelBase(text, size, x, uiStep(), false);
}

// ====================================== TOOLTIP =======================================
//X and Y are the position of the point of the triangle
public void Tooltip(String text, int x, int y) {
  textSize(largeFontSize);
  int w = (int)textWidth(text);
  int h = 50;
  int tw = 14; //triangle width
  int th = 15; //triangle height
  noStroke();
  //Shadow
  fill(0, 0, 0, 15);
  rect(5+x-w/2, 5+y-th-h, w, h, 2);
  triangle(5+x-tw/2, 5+y-th, 5+x, 5+y, 5+x+tw/2, 5+y-th);
  //Color
  fill(c_very_dark);
  rect(x-w/2, y-th-h, w, h, 2);
  triangle(x-tw/2, y-th, x, y, x+tw/2, y-th);
  //Text
  textSize(medFontSize);
  fill(255);
  textAlign(CENTER, CENTER);
  text(text, x-w/2, y-th-h, w, h);
  //triangle(
}

// ====================================== DIVIDER =======================================
public void Divider(int x, int y, int w) {
  noStroke();
  fill(c_light);
  rect(x, y+_step_y/2, w, s_stroke/2, s_stroke/4);
}
public void Divider(int y, int w) {
  Divider((width-w)/2, y, w);
}
public void Divider(int w) {
  Divider((width-w)/2, uiStep(), w);
}


// ====================================== BUTTON =======================================
//Basic Text Button
public boolean Button(String text, int x, int y, int w, int h) {
  _prevX = x + w;
  stroke(c_very_dark);
  if (mouseX >= x && mouseX <= x+w && 
    mouseY >= y && mouseY <= y+h && !_drop_open) {
    fill(c_hover);
    rect(x, y, w, h);
    fill(c_text_color);
    textSize(medFontSize);
    textAlign(CENTER, CENTER);
    text(text, x, y, w, h);
    if (clicked && canClick) {
      fill(c_light);
      rect(x, y, w, h);
      text(text, x, y, w, h);
      canClick = false;
      return true;
    }
  } else {
    fill(c_light);
    rect(x, y, w, h);
    fill(c_text_color);
    textSize(medFontSize);
    textAlign(CENTER, CENTER);
    text(text, x, y, w, h);
    return false;
  }

  return false;
}

public boolean Button(String text) {
  int x;
  if (_x_offs != 0) x = _x_offs;
  else x = (width-s_med)/2;
  return Button(text, x, uiStep(), s_med, s_height);
}

public boolean Button(String text, int x) {
  return Button(text, x, uiStep(), s_med, s_height);
}

public boolean Button(String text, int x, int y, int w) {
  return Button(text, x, y, w, s_height);
}

//Basic Text Button
public boolean Button(String text, int x, int y) {
  return Button(text, x, y, s_med, s_height);
}

//Basic Text Button
public boolean Button(String text, int x, int y, String t) {
  return Button(text, x, y, s_med, s_height, t);
}

//Button With Tooltip
public boolean Button(String text, int x, int y, int w, int h, String tooltip) {
  _prevX = x + w;
  if (mouseX >= x && mouseX <= x+w && 
    mouseY >= y && mouseY <= y+h && !_drop_open) {
    Tooltip(tooltip, x+w/2, y-1);
    fill(c_hover);
    rect(x, y, w, h);
    fill(c_text_color);
    textSize(medFontSize);
    textAlign(CENTER, CENTER);
    text(text, x, y, w, h);
    if (clicked && canClick) {
      fill(c_light);
      rect(x, y, w, h);
      text(text, x, y, w, h);
      canClick = false;
      return true;
    }
  } else {
    fill(c_light);
    rect(x, y, w, h);
    fill(c_text_color);
    textSize(medFontSize);
    textAlign(CENTER, CENTER);
    text(text, x, y, w, h);
    return false;
  }

  return false;
}

// ====================================== FONT AWESOME =======================================
//https://fontawesome.com/v5.15/icons?d=gallery&p=2
/*--Font Awesome Icons--
 based on: 
 https://github.com/encharm/Font-Awesome-SVG-PNG
 this Method loads the .svg files from /svg/ folder
 more icons can be added at the folder, and called by its names
 ----------------*/
//This hashmap is used to avoid reload the same icon multiple times
HashMap<String, PShape> usedIcons = new HashMap<String, PShape>();

public void Icon(String name, int x, int y, int w, int h) {
  _prevX = x + w;
  if (usedIcons.get(name)==null) {
    try {
      PShape i = loadShape(name+".svg");
      usedIcons.put(name, i);
    }
    catch(Exception e) {
      println("CARD_UI - ERROR: svg icon not found");
    }
  }
  PShape i = usedIcons.get(name);
  if (w == h && i.width != i.height) {
    if (i.width > i.height) {
      int H = PApplet.parseInt(w * i.height / i.width);
      y += (h - H) / 2;
      h = H;
    } else {
      int W = PApplet.parseInt(h * i.width / i.height);
      x += (w - W) / 2;
      w = W;
    }
  }
  shape(i, x, y, w, h);
}
public void Icon(String name, int x, int y, int w) {
  Icon(name, x, y, w, w);
}

// ====================================== IMAGE BUTTON =======================================
public boolean ImageButton(PImage img, int x, int y, int w, int h, boolean select) {
  _prevX = x + w;
  int p = min(w, h) / 10;
  int W = w-2*p, H = h-2*p;

  int dW = W;
  int dH = W * img.height / img.width;
  if (dH > H) {
    dH = H;
    dW = H * img.width / img.height;
  }

  int ix = x + (w - dW) / 2;
  int iy = y + (h - dH) / 2;

  if (mouseX >= x && mouseX <= x+w && 
    mouseY >= y && mouseY <= y+h && !_drop_open) {
    fill(c_hover);
    rect(x, y, w, h);
    image(img, ix, iy, dW, dH);
    if (clicked && canClick) {
      fill(c_light);
      rect(x, y, w, h);
      image(img, ix, iy, dW, dH);
      canClick = false;
      return true;
    }
  } else {
    if (select) fill(c_dark);
    else fill(c_light);
    rect(x, y, w, h);
    image(img, ix, iy, dW, dH);
    return false;
  }
  return false;
}

public boolean ImageButton(PImage img, int x, int y, int w, int h) {
  return ImageButton(img, x, y, w, h, false);
}

public boolean ImageButton(PImage img, int x, int y) {
  return ImageButton(img, x, y, s_height, s_height, false);
}

public boolean ImageButton(PImage img, int x, int y, boolean select) {
  return ImageButton(img, x, y, s_height, s_height, select);
}

public boolean ImageButton(PImage img, int x) {
  return ImageButton(img, x, uiStep(), s_height, s_height, false);
}

public boolean ImageButton(PImage img, int x, boolean select) {
  return ImageButton(img, x, uiStep(), s_height, s_height, select);
}

public boolean ImageButton(PImage img) {
  int x;
  if (_x_offs != 0) x = _x_offs;
  else x = (width-s_height)/2;
  return ImageButton(img, x, uiStep(), s_height, s_height, false);
}

public boolean ImageButton(PImage img, boolean select) {
  return ImageButton(img, (width-s_height)/2, uiStep(), s_height, s_height, select);
}

// ====================================== ICON BUTTON =======================================
public boolean IconButton(String icon, int x, int y, int w, int h, boolean select) {
  _prevX = x + w;
  int p=min(w, h)/9;
  int ix=x+p;
  int iy=y+p;
  int iw=min(w, h)-2*p;

  if (w > h) ix = x+(w-iw)/2;
  else if (w < h) iy = y+(h-iw)/2;

  if (mouseX >= x && mouseX <= x+w && 
    mouseY >= y && mouseY <= y+h && !_drop_open) {
    fill(c_hover);
    rect(x, y, w, h);
    Icon(icon, ix, iy, iw);
    if (clicked && canClick) {
      fill(c_light);
      rect(x, y, w, h);
      Icon(icon, ix, iy, iw);
      canClick = false;
      return true;
    }
  } else {
    if (select) fill(c_dark);
    else fill(c_light);
    rect(x, y, w, h);
    Icon(icon, ix, iy, iw);
    return false;
  }
  return false;
}

public boolean IconButton(String icon, int x, int y, int w, int h) {
  return IconButton(icon, x, y, w, h, false);
}

public boolean IconButton(String icon, int x, int y) {
  return IconButton(icon, x, y, s_height, s_height, false);
}

public boolean IconButton(String icon, int x, int y, boolean select) {
  return IconButton(icon, x, y, s_height, s_height, select);
}

public boolean IconButton(String icon, int x) {
  return IconButton(icon, x, uiStep(), s_height, s_height, false);
}

public boolean IconButton(String icon, int x, boolean select) {
  return IconButton(icon, x, uiStep(), s_height, s_height, select);
}

public boolean IconButton(String icon) {
  int x;
  if (_x_offs != 0) x = _x_offs;
  else x = (width-s_height)/2;
  return IconButton(icon, x, uiStep(), s_height, s_height, false);
}

public boolean IconButton(String icon, boolean select) {
  return IconButton(icon, (width-s_height)/2, uiStep(), s_height, s_height, select);
}

// ====================================== TEXT INPUT =======================================
public class TextInput {
  String text = "";
  boolean active = false;
  String hint = "";
  String label = "";

  public TextInput() {
  }

  public TextInput(String t) {
    this.hint = t;
  }

  public TextInput(String t, String l) {
    this.hint = t;
    this.label = l;
  }
  
  public TextInput(String t, boolean val) {
    this.text = t;
  }

  boolean done = false;
  public boolean done() {
    if (done) {
      done = false;
      return true;
    } 
    return false;
  }

  //Text Input
  public boolean show(int x, int y, int w, int h) {
    _prevX = x + w;
    boolean in = false;
    fill(200);
    textSize(smallFontSize);
    textAlign(LEFT, BOTTOM);
    text(label, x, y-21, w, 20);
    if (active) {
      //Edit Text
      fill(c_dark);
      stroke(c_light);
      rect(x, y, w, h);
      noStroke();
      fill(c_text_color);
      textSize(medFontSize);
      textAlign(CENTER, CENTER);
      text = bufferText;
      text(text, x, y, w, h);

      if (mouseX >= x && mouseX <= x+w && 
        mouseY >= y && mouseY <= y+h && !_drop_open) {
        //Inside
      } else {
        if (clicked) {
          doneText = true;
          //canClick = true;
          active=false;
        }
      }

      if (doneText) {
        done = true;
        text = bufferText;
        active = false;
        doneText = false;
      }
    } else if (mouseX >= x && mouseX <= x+w && 
      mouseY >= y && mouseY <= y+h && !_drop_open) {
      fill(c_hover);
      rect(x, y, w, h);
      fill(c_text_color);
      textSize(medFontSize);
      textAlign(CENTER, CENTER);
      text(text, x, y, w, h);
      if (clicked && canClick) {
        fill(c_light);
        rect(x, y, w, h);
        fill(255);
        text(text, x, y, w, h);
        EditText(text);
        canClick = false;
        active = true;
        in = true;
      }
    } else {
      fill(c_light);
      stroke(c_dark);
      rect(x, y, w, h);
      fill(c_text_color);
      textSize(medFontSize);
      textAlign(CENTER, CENTER);
      text(text, x, y, w, h);
      active = false;
    }
    if (text.length() == 0) {
      fill(150);
      textSize(medFontSize);
      textAlign(CENTER, CENTER);
      text(hint, x, y, w, h);
    }
    return in;
  }

  public boolean show(int x, int y, int w) {
    return show(x, y, w, s_height);
  }

  public boolean show(int x, int w) {
    return show(x, uiStep(), w, s_height);
  }

  public boolean show(int x) {
    return show(x, uiStep(), s_med, s_height);
  }

  public boolean show() {
    int x;
    if (_x_offs != 0) x = _x_offs;
    else x = (width-s_med)/2;
    return show(x, uiStep(), s_med, s_height);
  }

  public String getText() {
    return text;
  }
}


// ====================================== CARD =======================================
//c_mid
public void beginCard(String card_title, int x, int y, int w, int h) {
  _prevX = x + w;
  noStroke();
  //Shadow
  fill(0, 0, 0, 15);
  rect(x+5, y+5, w, h);
  fill(c_light);
  rect(x, y, w, 40, 2, 2, 0, 0);
  textSize(medFontSize);
  textAlign(CENTER, CENTER);
  fill(c_text_color);
  text(card_title, x, y, w, 40);
  fill(c_mid);

  rect(x, y+40, w, h-40, 0, 0, 2, 2);

  card_h = h-40;
  card_w = w;
  card_x = x;
  card_y = y+40;
  //uiLight();
}

public void beginCard(int x, int y, int w, int h) {
  noStroke();
  fill(c_mid);

  rect(x, y, w, h);

  card_h = h;
  card_w = w;
  card_x = x;
  card_y = y;
  //uiDark();
}

public void endCard() {
  card_h = 0;
  card_w = 0;
  card_y = 0;
  card_x = 0;
}

// ====================================== TOGGLE =======================================
public class Toggle {
  public Toggle() {
  }

  boolean value = false;

  public boolean show(int x, int y, int w, int h) {
    _prevX = x + w;
    fill(c_dark);
    stroke(c_light);
    rect(x, y, w, h, h);
    noStroke();
    int pos = 0;
    if (value) pos = w-h;
    //Hover
    if (mouseX >= x && mouseX <= x+w && mouseY >= y && mouseY <= y+h && !_drop_open) {
      fill(red(c_hover), green(c_hover), blue(c_hover), 100);  
      circle(x+h/2+pos, y+h/2, h-2);
      fill(c_hover);
      circle(x+h/2+pos, y+h/2, h-s_stroke);
      if (clicked && canClick) {
        value = !value;
        canClick = false;
        return true;
      }
    } 
    //Normal
    else {
      if (value) fill(c_hover);
      else fill(c_light);
      circle(x+h/2+pos, y+h/2, h-s_stroke);
    }

    return false;
  }

  public boolean show(int x, int y) {
    return show(x, y, s_small, s_height);
  }

  public boolean show(int x) {
    return show(x, uiStep(), s_small, s_height);
  }

  public boolean show() {
    int x;
    if (_x_offs != 0) x = _x_offs;
    else x = (width-s_small)/2;
    return show(x, uiStep(), s_small, s_height);
  }

  public boolean show(String text, int x, int y, int w, int h) {
    textSize(medFontSize);
    fill(c_text_color);
    textAlign(LEFT, CENTER);
    text(text, x, y, w, h);
    int pos_x = (int)textWidth(text);
    return show(x+10+pos_x, y, s_small, s_height);
  }

  public boolean show(String text, int x, int y) {
    return show(text, x, y, s_small, s_height);
  }

  public boolean show(String text, int x) {
    return show(text, x, uiStep(), s_small, s_height);
  }

  public boolean show(String text) {
    int x;
    if (_x_offs != 0) x = _x_offs;
    else x = (width-s_small)/2;
    return show(text, x, uiStep(), s_small, s_height);
  }
}

// ====================================== RADIO BUTTON =======================================
public class RadioButton {
  public RadioButton() {
  }
  boolean value = false;

  public boolean show(int x, int y, int w) {
    _prevX = x + w;
    noStroke();
    if (value) fill(c_hover);
    else fill(c_light);

    if (mouseX >= x && mouseX <= x+w && mouseY >= y && mouseY <= y+w && !_drop_open) {  //Hover
      fill(red(c_hover), green(c_hover), blue(c_hover), 100);
      circle(x+w/2, y+w/2, w-2);
      if (value) fill(c_hover);
      else fill(c_light);
      circle(x+w/2, y+w/2, w-s_stroke);
      if (clicked && canClick) {
        value = !value;
        _item_changed = true;
        canClick = false;
        return true;
      }
    } else {
      circle(x+w/2, y+w/2, w-s_stroke);
    }
    return false;
  }

  public boolean show(int x, int y) {
    return show(x, y, s_height);
  }

  public boolean show(int x) {
    return show(x, uiStep(), s_height);
  }

  public boolean show() {
    int x;
    if (_x_offs != 0) x = _x_offs;
    else x = (width-s_height)/2;
    return show(x, uiStep(), s_height);
  }

  public boolean show(String text, int x, int y, int w) {
    textSize(medFontSize);
    fill(c_text_color);
    textAlign(LEFT, CENTER);
    text(text, x, y+w/2);
    int pos_x = (int)textWidth(text);
    return show(x+10+pos_x, y, s_height);
  }

  public boolean show(String text, int x, int y) {
    return show(text, x, y, s_height);
  }

  public boolean show(String text, int x) {
    return show(text, x, uiStep(), s_height);
  }

  public boolean show(String text) {
    return show(text, (width-s_height)/2, uiStep(), s_height);
  }
}

// ====================================== SLIDER =======================================
public class Slider {
  public Slider() {
  }

  public float value = 0;
  public int tmr = 0;
  public boolean flag = false;

  public boolean show(float min, float max, int x, int y, int w, int h) {
    _prevX = x + w;
    boolean chFlag = false;
    noStroke();
    fill(c_light);
    rect(x, y+h/2, w, 4, 2);
    value = constrain(value, min, max);
    float pos = map(value, min, max, 0, w);
    //pos = constrain(pos, min, max);
    fill(c_hover);
    rect(x, y+h/2, pos, 4, 2);

    //Hover
    if (mouseX >= x && mouseX <= x+w && mouseY >= y && mouseY <= y+h && !_drop_open) {
      fill(c_hover);
      if (mousePressed && canClick) {
        pos = mouseX;
        _item_changed = true;
        chFlag = true;
        flag = true;
        value = map(pos, x, x+w, min, max);        
        fill(red(c_hover), green(c_hover), blue(c_hover), 100);
        ellipse(pos, y+h/2, h, h); 
        fill(c_hover);
        ellipse(pos, y+h/2, h-s_stroke, h-s_stroke);
      } else {
        fill(red(c_hover), green(c_hover), blue(c_hover), 50);
        ellipse(pos+x, y+h/2, h, h); 
        fill(c_hover);
        ellipse(pos+x, y+h/2, h-s_stroke, h-s_stroke);
      }
    } 
    //Normal
    else {
      noStroke();
      fill(c_hover);
      ellipse(pos+x, y+h/2, h-s_stroke, h-s_stroke);
    }
    if ((chFlag && millis() - tmr > 50) || (!chFlag && flag)) {
      tmr = millis();
      flag = false;
      return true;
    }
    return false;
  }

  public boolean show(String label, float min, float max, int x, int y, int w, int h) {
    _prevX = x + w;
    int w2 = 0;
    textSize(medFontSize);
    float tw = textWidth(label);
    textSize(medFontSize);
    fill(255);
    textAlign(LEFT, CENTER);
    text(label, x, y, tw, h);
    w2 = (int)(w-tw-15);
    return show(min, max, (int)(tw+x+15), y, w2, h);
  }

  //Minimal Slider
  public boolean show() {
    int x;
    if (_x_offs != 0) x = _x_offs;
    else x = (width-s_height)/2;
    return show(0f, 1f, x, uiStep(), s_big, s_height);
  }

  public boolean show(int x) {
    return show(0f, 1f, x, uiStep(), s_big, s_height);
  }

  public boolean show(int x, int y) {
    return show(0f, 1f, x, y, s_big, s_height);
  }

  public boolean show(String label, int x, int y) {
    return show(label, 0f, 1f, x, y, s_big, s_height);
  }

  public boolean show(String label, int x, int y, int w, int h) {
    return show(label, 0f, 1f, x, y, w, h);
  }

  public boolean show(int x, int y, int w) {
    return show(0f, 1f, x, y, w, s_height);
  }
  public boolean show(int x, int y, int w, int h) {
    return show(0f, 1f, x, y, w, h);
  }

  public boolean show(float min, float max, int x, int y, int w) {
    return show((float) min, (float) max, x, y, w, s_height);
  }
}

// ====================================== HANDLER =======================================
public void pressHandler() {
  clicked = true;
}

public void releaseHandler() {
  clicked = false;
  canClick = true;
}

public void keyHandler() {
  if (keyCode == BACKSPACE) {
    if (bufferText.length() > 0) {
      bufferText = bufferText.substring(0, bufferText.length()-1);
    }
  } else if (keyCode == DELETE) {
    bufferText = "";
  } else if (keyCode != SHIFT && keyCode != ENTER) {
    bufferText = bufferText + key;
  }

  if (keyCode == ' ') {
    bufferText = bufferText.substring(0, bufferText.length()-1);
    bufferText = bufferText + ' ';
  }

  if (keyCode == ENTER) {
    //input = myText;
    //bufferText = "";
    doneText = true;
  }
}

// ====================================== DROPDOWN =======================================
public class DropDown {
  private int selected = 0;
  private boolean open = false;

  public DropDown() {
  }

  public boolean show(String args[], int x, int y, int w) {
    _prevX = x + w;
    int h = s_height;
    w -= h;
    _drop_open = false;
    if (Button(args[selected], x, y, w, h) || Button("", x+w, y, h, h)) open = !open;
    noStroke();
    if (!open) {
      triangle(x+w+h/4+1, y+0.28f*h+1, x+w+h*3/4+1, y+0.28f*h+1, x+w+h/2+1, y+0.71f*h+1);
      stroke(c_very_dark);
    } else {
      triangle(x+w+h/4+1, y+0.71f*h+1, x+w+h*3/4+1, y+0.71f*h+1, x+w+h/2+1, y+0.28f*h+1);
      stroke(c_very_dark);
      fill(c_light);
      rect(x+0, y+h, w+h, s_height*args.length);
      for (int i=0; i<args.length; i++) {
        if (Button(args[i], x, y + h + h*i, w+h, h)) {
          _drop_open = false;
          open = false;
          selected = i;
          canClick = false;
          return true;
        }
      }
      _drop_open = open;
    }
    return false;
  }

  public boolean show(String args[], int x, int y) {
    return show(args, x, y, maxLen(args)+medFontSize+s_height);
  }

  public boolean show(String args[], int x) {
    return show(args, x, uiStep());
  }

  public boolean show(String args[]) {
    int x;
    if (_x_offs != 0) x = _x_offs;
    else x = (width-maxLen(args)-medFontSize-s_height)/2;
    return show(args, x, uiStep());
  }

  public int getSelected() {
    return selected;
  }

  public int maxLen(String args[]) {
    int w = 0;
    textSize(medFontSize);
    for (int i = 0; i < args.length; i++) {
      int tw = (int)textWidth(args[i]);
      w = max(w, tw);
    }
    return w;
  }
}


// ============================== DEPRECATED =============================
//Toggle
public boolean Toggle(boolean value, int x, int y, int w, int h) {
  _prevX = x + w;
  fill(c_dark);
  stroke(c_light);
  rect(x, y, w, h, h);
  noStroke();
  int pos = 0;
  if (value) pos = w-h;
  //Hover
  if (mouseX >= x && mouseX <= x+w && mouseY >= y && mouseY <= y+h && !_drop_open) {
    fill(red(c_hover), green(c_hover), blue(c_hover), 100);  
    circle(x+h/2+pos, y+h/2, h-2);
    fill(c_hover);
    circle(x+h/2+pos, y+h/2, h-s_stroke);
    if (clicked && canClick) {
      value = !value;
      _item_changed = true;
      canClick = false;
      return value;
    }
  } 
  //Normal
  else {
    if (value) fill(c_hover);
    else fill(c_light);
    circle(x+h/2+pos, y+h/2, h-s_stroke);
  }

  return value;
}

public boolean Toggle(boolean value, int x, int y) {
  return Toggle(value, x, y, s_small, s_height);
}

public boolean Toggle(boolean value, int x) {
  return Toggle(value, x, uiStep(), s_small, s_height);
}

public boolean Toggle(boolean value) {
  int x;
  if (_x_offs != 0) x = _x_offs;
  else x = (width-s_small)/2;
  return Toggle(value, x, uiStep(), s_small, s_height);
}

public boolean Toggle(String text, boolean value, int x, int y, int w, int h) {
  textSize(medFontSize);
  fill(c_text_color);
  textAlign(LEFT, CENTER);
  text(text, x, y, w, h);
  int pos_x = (int)textWidth(text);
  return Toggle(value, x+10+pos_x, y, s_small, s_height);
}

public boolean Toggle(String text, boolean value, int x, int y) {
  return Toggle(text, value, x, y, s_small, s_height);
}

public boolean Toggle(String text, boolean value, int x) {
  return Toggle(text, value, x, uiStep(), s_small, s_height);
}

public boolean Toggle(String text, boolean value) {
  int x;
  if (_x_offs != 0) x = _x_offs;
  else x = (width-s_small)/2;
  return Toggle(text, value, x, uiStep(), s_small, s_height);
}


//Toggle
public boolean RadioButton(boolean value, int x, int y, int w) {
  _prevX = x + w;
  noStroke();
  if (value) fill(c_hover);
  else fill(c_light);

  if (mouseX >= x && mouseX <= x+w && mouseY >= y && mouseY <= y+w && !_drop_open) {  //Hover
    fill(red(c_hover), green(c_hover), blue(c_hover), 100);
    circle(x+w/2, y+w/2, w-2);
    if (value) fill(c_hover);
    else fill(c_light);
    circle(x+w/2, y+w/2, w-s_stroke);
    if (clicked && canClick) {
      value = !value;
      _item_changed = true;
      canClick = false;
      return value;
    }
  } else {
    circle(x+w/2, y+w/2, w-s_stroke);
  }
  return value;
}

public boolean RadioButton(boolean value, int x, int y) {
  return RadioButton(value, x, y, s_height);
}

public boolean RadioButton(boolean value, int x) {
  return RadioButton(value, x, uiStep(), s_height);
}

public boolean RadioButton(boolean value) {
  int x;
  if (_x_offs != 0) x = _x_offs;
  else x = (width-s_height)/2;
  return RadioButton(value, x, uiStep(), s_height);
}

public boolean RadioButton(String text, boolean value, int x, int y, int w) {
  textSize(medFontSize);
  fill(c_text_color);
  textAlign(LEFT, CENTER);
  text(text, x, y+w/2);
  int pos_x = (int)textWidth(text);
  return RadioButton(value, x+10+pos_x, y, s_height);
}

public boolean RadioButton(String text, boolean value, int x, int y) {
  return RadioButton(text, value, x, y, s_height);
}

public boolean RadioButton(String text, boolean value, int x) {
  return RadioButton(text, value, x, uiStep(), s_height);
}

public boolean RadioButton(String text, boolean value) {
  return RadioButton(text, value, (width-s_height)/2, uiStep(), s_height);
}

//Basic Slider from 0f to 1f
public float Slider(float min, float max, float value, int x, int y, int w, int h) {
  _prevX = x + w;
  noStroke();
  fill(c_light);
  rect(x, y+h/2, w, 4, 2);
  float pos = map(value, min, max, 0, w);
  fill(c_hover);
  rect(x, y+h/2, pos, 4, 2);

  //Hover
  if (mouseX >= x && mouseX <= x+w && mouseY >= y && mouseY <= y+h && !_drop_open) {
    fill(c_hover);
    if (mousePressed && canClick) {
      pos = mouseX;
      _item_changed = true;
      value = map(pos, x, x+w, min, max);
      fill(red(c_hover), green(c_hover), blue(c_hover), 100);
      ellipse(pos, y+h/2, h, h); 
      fill(c_hover);
      ellipse(pos, y+h/2, h-s_stroke, h-s_stroke);
    } else {
      fill(red(c_hover), green(c_hover), blue(c_hover), 50);
      ellipse(pos+x, y+h/2, h, h); 
      fill(c_hover);
      ellipse(pos+x, y+h/2, h-s_stroke, h-s_stroke);
    }
  } 
  //Normal
  else {
    noStroke();
    fill(c_hover);
    ellipse(pos+x, y+h/2, h-s_stroke, h-s_stroke);
  }

  return value;
}

public float Slider(String label, float min, float max, float value, int x, int y, int w, int h) {
  _prevX = x + w;
  int w2 = 0;
  textSize(medFontSize);
  float tw = textWidth(label);
  textSize(medFontSize);
  fill(255);
  textAlign(LEFT, CENTER);
  text(label, x, y, tw, h);
  w2 = (int)(w-tw-15);
  return Slider(min, max, value, (int)(tw+x+15), y, w2, h);
}

//Minimal Slider
public float Slider(float value) {
  int x;
  if (_x_offs != 0) x = _x_offs;
  else x = (width-s_height)/2;
  return Slider(0f, 1f, value, x, uiStep(), s_big, s_height);
}

public float Slider(float value, int x) {
  return Slider(0f, 1f, value, x, uiStep(), s_big, s_height);
}

public float Slider(float value, int x, int y) {
  return Slider(0f, 1f, value, x, y, s_big, s_height);
}

public float Slider(String label, float value, int x, int y) {
  return Slider(label, 0f, 1f, value, x, y, s_big, s_height);
}

public float Slider(String label, float value, int x, int y, int w, int h) {
  return Slider(label, 0f, 1f, value, x, y, w, h);
}

public float Slider(float value, int x, int y, int w) {
  return Slider(0f, 1f, value, x, y, w, s_height);
}
public float Slider(float value, int x, int y, int w, int h) {
  return Slider(0f, 1f, value, x, y, w, h);
}

public float Slider(float min, int max, int value, int x, int y, int w, int h) {
  return Slider((float) min, (float) max, value, x, y, w, h);
}
public float Slider(float min, int max, int value, int x, int y, int w) {
  return Slider((float) min, (float) max, value, x, y, w, s_height);
}
public void startSearch() {
  int[] ipv4 = PApplet.parseInt(split(KetaiNet.getIP(), '.'));
  int[] mask = PApplet.parseInt(split(subnet.text, '.'));
  found = false;
  curIP = "";
  brIP = "";
  for (int i = 0; i < 4; i++) {
    brIP += ipv4[i] | (mask[i] ^ 0xFF);
    if (i != 3) brIP += '.';
  }

  searchF = true;
  parseMode = 0;

  // выводим однократно
  ips.clear();
  ips.append("searching...");
  dropIP.selected = 0;
  ui();
  ips.clear();

  curIP = brIP;
  sendData(new int[] {0});
  actionTmr = millis();
}

public void requestCfg() {
  parseMode = 1;
  int[] buf = {1};
  sendData(buf);
}

public void sendData(int[] data) {
  int[] buf = {'G', 'T'};
  buf = concat(buf, data);
  sendData(PApplet.parseByte(buf));
}

public void sendData(byte[] data) {  
  if (curIP.charAt(0) != 'n') {
    udp.send(data, curIP, port);
    delay(15);
    udp.send(data, curIP, port);
  }
}
public void receive(byte[] ubuf) {
  if (ubuf[0] != 'G' || ubuf[1] != 'T') return;
  int[] data = new int[10];
  for (int i = 0; i < ubuf.length - 2; i++) {
    data[i] = PApplet.parseInt(ubuf[i+2]);
    //println(data[i]);
  }

  if (parseMode != data[0]) return;

  switch (data[0]) {
  case 0: // Поиск
    String ip = brIP.substring(0, brIP.lastIndexOf('.')+1) + str(data[1]);
    if (!ips.hasValue(ip)) ips.append(ip);
    break;

  case 1: // Настройки 
    searchF = false;
    leds.text = str(data[1] * 100 + data[2]);
    power.value = PApplet.parseBoolean(data[3]);
    bri.value = data[4];
    auto.value = PApplet.parseBoolean(data[5]);
    rnd.value = PApplet.parseBoolean(data[6]);
    prd.value = data[7];
    offT.value = PApplet.parseBoolean(data[8]);
    offS.value = data[9];
    break;

  case 4: // Эффект
    fav.value = PApplet.parseBoolean(data[1]);
    scl.value = data[2];
    spd.value = data[3];
    break;
  }
}
}
