(ns harja.views.urakka.kustannusten-kirjaus
  "Tiemerkintäurakan Kustannukset-välilehti"
  (:require
    [harja.ui.bootstrap :as bs]
    [harja.ui.komponentti :as komp]
    [harja.views.urakka.tiemerkinta-kustannukset.tiemerkintojen-korjaus :as tiemerkintojen-korjaus]
    [harja.views.urakka.tiemerkinta-kustannukset.uusien-paallysteiden-merkinnat-nakyma :as paallysteiden-merkinnat]
    [harja.views.urakka.tiemerkinta-kustannukset.sakot-ja-bonukset :as sakot-ja-bonukset]
    [harja.views.urakka.tiemerkinta-kustannukset.muut-kustannukset :as muut-kustannukset]
    [harja.tiedot.navigaatio :as nav]))

(defn kustannusten-kirjaus []
  (komp/luo
    (fn []
       [bs/tabs {:style :tabs :classes "tabit-tiivis-keski"
                 :active (nav/valittu-valilehti-atom :kustannusten-kirjaus)}

        "Tiemerkintöjen korjaus"
        :tiemerkintojen-korjaus
        [tiemerkintojen-korjaus/tiemerkintojen-korjaus]

        "Uusien päällysteiden merkinnät"
        :uusien-pallysteiden-merkinnat
        [paallysteiden-merkinnat/uusien-paallysteiden-merkinnat]

        "Sakot ja bonukset"
        :sakot-ja-bonukset
        [sakot-ja-bonukset/sakot-ja-bonukset]

        "Muut kustannukset"
        :muut-kustannukset
        [muut-kustannukset/muut-kustannukset]])))
