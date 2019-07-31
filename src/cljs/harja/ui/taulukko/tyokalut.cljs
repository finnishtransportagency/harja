(ns harja.ui.taulukko.tyokalut
  (:require [harja.loki :as loki]
            [harja.ui.taulukko.protokollat :as p]))

(defn numero-re
  ([] (numero-re {}))
  ([{kokonaisosan-maara :kokonaisosan-maara desimaalien-maara :desimaalien-maara
     positiivinen? :positiivinen? kokonaisluku? :kokonaisluku?
     :or {kokonaisosan-maara 10 desimaalien-maara 10 positiivinen? false kokonaisluku? false}}]
   (str (when-not positiivinen? "-?")
        "\\d{1," kokonaisosan-maara "}"
        (when-not kokonaisluku? (str "((\\.|,)\\d{0," desimaalien-maara "})?")))))

(defn positiivinen-numero-re
  ([] (positiivinen-numero-re {}))
  ([asetukset]
    (numero-re (assoc asetukset :positiivinen? true))))

(defmulti lisaa-kaytos
          (fn [kaytos _]
            (cond
              (map? kaytos) (first (keys kaytos))
              (keyword? kaytos) kaytos
              :else nil)))

(defmethod lisaa-kaytos :eventin-arvo
  [_ toiminto]
  (comp toiminto
        (fn [event]
          (.. event -target -value))))

(defmethod lisaa-kaytos :positiivinen-numero
  [_ toiminto]
  (comp toiminto
        (fn [arvo]
          (let [positiivinen-arvo? (re-matches (re-pattern (positiivinen-numero-re)) arvo)]
            (when (or (= "" arvo) positiivinen-arvo?)
              arvo)))))

(defmethod lisaa-kaytos :default
  [kaytos toiminto]
  (loki/warn "KAYTOSTÄ: " kaytos " EI OLE MÄÄRITETTY!")
  toiminto)

(defn lisaa-kaytokset
  "Toiminnot on map, jossa avaimet vastaa input elementin saamia parametrejä. Nämä ovat siis on-change, on-key-down jne.
   Toiminnot arvot on funktio, joka saa parametrina kayttaytymisfunktion palauttaman arvon tai arvot.
   Kayttaytymiset on myös map, jossa avaimet vastaa toimintojen avaimia. Näiden avainten avulla kohdistetaan käyttäytymiset
   oikealle toiminnolle. Kayttaytymisen arvo taasen on funktioita sisältävä sequable, jonka järjestyksellä on väliä!

   Sequablen viimeinen funktio saa eventin argumentikseen. Tämän palauttama arvo annetaan toiseksi viimeiselle
   käyttäytymiselle jne. Lopuksi arvo annetaan itse toiminnolle.

   Esim.
   (let [tila-atom (atom nil)
         toiminnot {:on-change (fn [arvo] (reset! tila-atom arvo))}
         kayttaytymiset {:on-change [:positiivinen-numero :eventin-arvo]}

         ;; Tässä lopputuloksena on funktio, jossa ensin oletetaan saavan javascript event (:eventin-arvo käyttäytyminen),
         ;; jonka arvo annetaan :positiivinen-numero käyttäytymiselle, joka puolestaan antaa loppputuloksen toiminnolle,
         ;; joka resetoi tila-atomin arvon.
         f-map (lisaa-kaytokset toiminnot kayttaytymiset)]
     [:input {:value @tila-atom :on-change (:on-change f-map)}])"
  [toiminnot kayttaytymiset]
  (merge-with (fn [kayttaytymiset toiminto]
                (loop [[kaytos & loput-kaytokset] kayttaytymiset
                       lopullinen-toiminto toiminto]
                  (if (nil? kaytos)
                    lopullinen-toiminto
                    (recur loput-kaytokset
                           (lisaa-kaytos kaytos lopullinen-toiminto)))))
              kayttaytymiset
              toiminnot))

(defn jana
  "Palauttaa jana(t) joiden id vastaa annettua"
  [taulukko id]
  (filter #(p/janan-id? % id) taulukko))

(defn janan-osa
  "Palauttaa janan elementi(t) joiden id vastaa annettua"
  [jana id]
  (filter #(p/osan-id? % id) (p/janan-osat jana)))

(defn janan-index
  "Palauttaa janan indeksin taulukossa"
  [taulukko jana]
  (first (keep-indexed #(when (= (p/janan-id %2) (p/janan-id jana))
                          %1)
                       taulukko)))