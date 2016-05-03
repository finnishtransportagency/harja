(ns harja.views.hallinta.api-jarjestelmatunnukset
  "Harja API:n järjestelmätunnuksien listaus ja muokkaus."
  (:require [harja.ui.grid :as grid]
            [harja.pvm :as pvm]))

(defn api-jarjestelmatunnukset []

  [grid/grid {:otsikko "API järjestelmätunnukset"}
   [{:otsikko "Tunnus"
     :nimi :tunnus}
    {:otsikko "Nimi"
     :nimi :nimi}
    {:otsikko "Urakoitsija"
     :nimi :urakoitsija}
    {:otsikko "Urakat"
     :nimi :urakat}
    {:otsikko "Luotu"
     :nimi :luotu
     :tyyppi :pvm
     :fmt pvm/pvm-aika}
    ]

   [{:tunnus "Foo" :urakoitsija "Barsky" :urakat "Oulun au, Jokumuu HJU"
     :nimi "Softatoimittaja X" :luotu (pvm/luo-pvm 2016 4 2)}]
   ]
  )
