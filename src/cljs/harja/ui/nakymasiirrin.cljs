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

(defn- siirry [e keskita & [lapsi-elementti]]
  (let [hyppyjen-maara (/ nopeus liikkumis-intervalli)
        dokumentin-ylaosa (siirry-ylos)
        vali (/ (- (elementin-ylaosa e 0) dokumentin-ylaosa) hyppyjen-maara)
        vali (if keskita (- vali 10) vali)]
    (doseq [i (range 1 (inc hyppyjen-maara))]
      (let [hypyn-kohta (* vali i)
            siirry (+ hypyn-kohta dokumentin-ylaosa)
            timeout (* liikkumis-intervalli i)]
        (.setTimeout js/window (fn []
                                 ;; Jos halutaan scrollata sisempää näkymää eikä ulointa
                                 (if lapsi-elementti
                                   (.scrollTo (.getElementById js/document lapsi-elementti) 0 siirry)
                                   (.scrollTo js/window 0 siirry)))
                     timeout)))))

(defn kohde-elementti-id [kohde-id]
  (siirry (.getElementById js/document kohde-id) false))

(defn kohde-elementti-luokka [kohde-luokka]
  (let [selectable-rows (.getElementsByClassName js/document kohde-luokka)]
    (doall (map #(siirry % true) (array-seq selectable-rows)))))

(defn siirry-elementin-id [id aika]
  (kohde-elementti-id id)
  (.setTimeout js/window (fn [] (kohde-elementti-id id)) aika))

(defn siirry-lapsi-elementissa [kohde-id keskita e]
  (siirry (.getElementById js/document kohde-id) keskita e))
