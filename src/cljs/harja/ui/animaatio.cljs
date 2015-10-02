(ns harja.ui.animaatio
  "Apureita CSS animaatioiden kanssa työskentelyyn")

(defn- paattele-tuettu-transition-end []
  (let [elt (.createElement js/document "fakeelement")
        s (.-style elt)]
    (some (fn [[transitio event]]
            (when-not (= js/undefined (aget s transitio))
              event))
          [["transition" "transitionend"]
           ["OTransition" "oTransitionEnd"]
           ["MozTransition" "transitionEnd"]
           ["WebkitTransition" "webkitTransitionEnd"]])))

(def transition-end (paattele-tuettu-transition-end))

(defn transition-end-tuettu? []
  (not (nil? transition-end)))

(defn kasittele-transition-end [elt callback]
  (when (transition-end-tuettu?)
    (.addEventListener elt transition-end callback)))
