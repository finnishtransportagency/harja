(ns harja.domain.tilannekuva.makrot)

(defmacro maarittele-suodattimet [& sym-nimi-otsikko]
  (let [id (atom 0)]
    `(do
       ~@(mapv
          (fn [sym-nimi-otsikko]
            `(do ~@(mapv
                    (fn [[sym nimi otsikko]]
                      `(def ~sym (harja.domain.tilannekuva/->Suodatin
                                  ~(swap! id inc) ~nimi ~otsikko)))
                    sym-nimi-otsikko)))
          (partition 16 16 [] sym-nimi-otsikko))
       (def ~'suodattimet-idlla
         (into {}
               (map (juxt :id identity))
               [~@(map first sym-nimi-otsikko)])))))
