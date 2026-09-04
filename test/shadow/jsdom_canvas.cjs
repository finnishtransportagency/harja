const canvasPrototype = global.HTMLCanvasElement.prototype;

canvasPrototype.getContext = function getContext() {
  return {
    canvas: this,
    fillStyle: null,
    fillRect() {}
  };
};

canvasPrototype.toDataURL = function toDataURL() {
  return "data:image/png;base64,";
};
