(ns harja.ui.taulukko.tyokalut)

(defn muodosta-rivi-skeemasta-1 [riviskeema sarakeskeema [skeeman-nimi rivin-taydennys-fn]]
  (let [{:keys [janan-tyyppi osat janat]} (get riviskeema skeeman-nimi)]
    `(-> (~(symbol (namespace janan-tyyppi)
                   (str "map->" (name janan-tyyppi)))
           nil)
         (harja.ui.taulukko.tyokalut/aseta-arvo :lapset
                                                ~(if janat
                                                   (mapv (fn [jana]
                                                           (let [uusi-jana-pohja (muodosta-rivi-skeemasta-1 riviskeema sarakeskeema [jana `identity])]
                                                             `(-> ~uusi-jana-pohja
                                                                  (harja.ui.taulukko.tyokalut/aseta-arvo :id ~jana))))
                                                         janat)
                                                   (mapv (fn [osa-skeema sarake]
                                                           `(-> (~(symbol (namespace osa-skeema)
                                                                          (str "map->" (name osa-skeema)))
                                                                  nil)
                                                                (harja.ui.taulukko.tyokalut/aseta-arvo :id ~sarake)))
                                                         osat
                                                         sarakeskeema)))
         ~rivin-taydennys-fn)))

(defmacro muodosta-taulukko [id riviskeema sarakeskeema rivi-maaritelmat parametrit]
  (let [rivi-maaritelmat (partition 2 rivi-maaritelmat)
        rivit (mapv (fn [rivin-maaritelma]
                     (muodosta-rivi-skeemasta-1 riviskeema sarakeskeema rivin-maaritelma))
                   rivi-maaritelmat)]
    `(harja.ui.taulukko.taulukko/->Taulukko ~id ~riviskeema ~sarakeskeema (into [] (flatten ~rivit)) ~parametrit)))
