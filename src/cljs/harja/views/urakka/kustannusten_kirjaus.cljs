(ns harja.views.urakka.kustannusten-kirjaus
  "Tiemerkintäurakan Kustannukset-välilehti"
  (:require
    [harja.ui.bootstrap :as bs]
    [harja.ui.komponentti :as komp]
    [harja.views.urakka.kustannusten-kirjaus.tiemerkintojen-korjaus :as tiemerkintojen-korjaus]
    [harja.views.urakka.kustannusten-kirjaus.uusien-paallysteiden-merkinnat :as paallysteiden-merkinnat]
    [harja.views.urakka.kustannusten-kirjaus.sakot-ja-bonukset :as sakot-ja-bonukset]
    [harja.views.urakka.kustannusten-kirjaus.muut-kustannukset :as muut-kustannukset]
    [harja.tiedot.navigaatio :as nav]))

(defn kustannusten-kirjaus [ur]
  (komp/luo
    (fn [{:keys [id] :as ur}]
      [:span.kustannusten-kirjaus
       [bs/tabs {:style :tabs :classes "tabs-tabs2"
                 :active (nav/valittu-valilehti-atom :kustannusten-kirjaus)}

        "Tiemerkintöjen korjaus"
        :tiemerkintojen-korjaus
        [tiemerkintojen-korjaus/tiemerkintojen-korjaus]

        "Uusien päällysteiden merkinnät"
        :uusien-pallysteiden-merkinnat
        [paallysteiden-merkinnat/paallysteiden-merkinnat]

        "Sakot ja bonukset"
        :sakot-ja-bonukset
        [sakot-ja-bonukset/sakot-ja-bonukset]

        "Muut kustannukset"
        :muut-kustannukset
        [muut-kustannukset/muut-kustannukset]]])))
