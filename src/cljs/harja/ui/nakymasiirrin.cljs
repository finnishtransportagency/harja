(ns harja.ui.nakymasiirrin)

(def nopeus 500)
(def liikkumis-intervalli 15)

(defn- siirry-ylos []
  (+ (.. js/document -body -scrollTop) (.. js/document -documentElement -scrollTop)))

(defn- elementin-ylaosa [e t]
  (if (and e (.-offsetParent e))
    (let [asiakkaan-ylaosa (or (.-clientTop e) 0)
          matka-ylaosasta (.-offsetTop e)]
      (+ t asiakkaan-ylaosa matka-ylaosasta (elementin-ylaosa (.-offsetParent e) t)))
    t))

(defn kohde-elementti-id [kohde-id]
  (let [e (.getElementById js/document kohde-id)
        hyppyjen-maara (/ nopeus liikkumis-intervalli)
        dokumentin-ylaosa (siirry-ylos)
        vali (/ (- (elementin-ylaosa e 0) dokumentin-ylaosa) hyppyjen-maara)]
    (doseq [i (range 1 (inc hyppyjen-maara))]
      (let [hypyn-kohta (* vali i)
            siirry (+ hypyn-kohta dokumentin-ylaosa)
            timeout (* liikkumis-intervalli i)]
        (.setTimeout js/window (fn []
                                 (.scrollTo js/window 0 siirry))
                     timeout)))))