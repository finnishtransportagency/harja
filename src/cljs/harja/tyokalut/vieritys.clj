(ns harja.tyokalut.vieritys)

(defmacro vieritettava-osio
  [{:keys [menukomponentti osionavigointikomponentti]} & osiot]
  `(let [osiot# (vector ~@osiot)
         avaimet# (filter keyword? osiot#)
         alkuvalikko# (or ~menukomponentti
                          (fn [at#]
                            [:div "valikko"
                             (for [a# at#]
                               [:span {:on-click (harja.tyokalut.vieritys/vierita a#)}
                                (name a#)])]))
         navigointi# (r/partial ~osionavigointikomponentti avaimet#)
         luo-osiot# (comp
                     (partition-by keyword?)
                     (mapcat harja.tyokalut.vieritys/tee-majakat)
                     (harja.tyokalut.vieritys/tee-navigointi navigointi#)
                     (filter #(not (keyword? %))))
         pohja# (keep identity
                      [:<>
                       [harja.tyokalut.vieritys/majakka ::top]
                       (when alkuvalikko# [alkuvalikko# avaimet#])])
         osiot-majakoineen# (into [] luo-osiot# osiot#)
         koko-homma# (vec (concat pohja# osiot-majakoineen#))]
     koko-homma#))