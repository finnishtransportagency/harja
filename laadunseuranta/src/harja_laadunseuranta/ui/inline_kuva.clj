(ns harja-laadunseuranta.ui.inline-kuva)

(defmacro inline-svg [svg-tiedosto]
  [:span.inline-svg {:dangerouslySetInnerHTML {:__html (slurp svg-tiedosto)}}])
