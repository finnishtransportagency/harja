(ns harja-laadunseuranta.inline-kuva)

(defmacro inline-svg [svg-tiedosto]
  [:span.inline-svg {:dangerouslySetInnerHTML {:__html (slurp svg-tiedosto)}}])
