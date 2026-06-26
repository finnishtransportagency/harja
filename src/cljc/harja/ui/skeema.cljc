(ns harja.ui.skeema)

(defn laske-sarakkeiden-leveys [skeema]
  (let [parsi-luku-fn #?(:cljs #(.toFixed % 1) :clj #(Double/toString %))
        parsi-prosentti (fn [arvo yhteensa]
                          (str (parsi-luku-fn (* 100.0 (/ arvo yhteensa))) "%"))

        ;; Lasketaan :leveys prosentit (käytetään UI:ssa ja PDF:ssä)
        skeema (if (every? number? (map :leveys skeema))
                 (let [yhteensa (reduce + (map :leveys skeema))]
                   (mapv (fn [{lev :leveys :as kentta}]
                           (assoc kentta :leveys (parsi-prosentti lev yhteensa)))
                         skeema))
                 skeema)

        ;; Lasketaan myös :leveys-pdf prosentit, jos kaikki sarakkeet sisältävät sen numerona.
        ;; Käytetään vain PDF-renderöijässä, UI ei huomioi :leveys-pdf-avainta.
        leveys-pdf-arvot (keep :leveys-pdf skeema)
        skeema (if (and (seq leveys-pdf-arvot)
                     (= (count leveys-pdf-arvot) (count skeema))
                     (every? number? leveys-pdf-arvot))
                 (let [yhteensa (reduce + leveys-pdf-arvot)]
                   (mapv (fn [{pdf-lev :leveys-pdf :as kentta}]
                           (assoc kentta :leveys-pdf (parsi-prosentti pdf-lev yhteensa)))
                         skeema))
                 skeema)]
    skeema))
