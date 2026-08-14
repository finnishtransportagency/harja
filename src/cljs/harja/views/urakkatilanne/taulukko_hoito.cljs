(ns harja.views.urakkatilanne.taulukko-hoito
  (:require [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.urakkatilanne.kojelauta :as tiedot]

            [harja.views.urakkatilanne.sarakkeet.lupaukset :refer [lupauspisteet-sarake]]
            [harja.views.urakkatilanne.sarakkeet.valikatselmus :refer [valikatselmus-sarake]]
            [harja.views.urakkatilanne.sarakkeet.poikkeamat :refer [avoimet-poikkeamat-sarake]]
            [harja.views.urakkatilanne.sarakkeet.kustannussuunnitelma :refer [kustannussuunitelman-tila-sarake]]))

(defonce +urakoiden-maara-per-sivu+ 20)


(defn taulukko-hoitourakat [_e! {:keys [urakat haku-kaynnissa?]}]
  [grid/grid
   {:otsikko (str "")
    :sivuta +urakoiden-maara-per-sivu+
    :tyhja (if haku-kaynnissa?
             [ajax-loader "Ladataan tietoja"]
             "Ei tietoja, tarkistathan valitut suodattimet.")
    :rivi-jalkeen-fn (fn [urakat]
                       (let [ks-tilojen-yhteenveto (tiedot/ks-tilojen-yhteenveto urakat)
                             valikatselmus-tilojen-yhteenveto (tiedot/valikatselmus-tilojen-yhteenveto urakat)
                             lupaustietojen-yhteenveto (tiedot/lupaustietojen-yhteenveto urakat)
                             poikkeusten-yhteenveto (tiedot/poikkeusten-yhteenveto urakat)]
                         (when-not (empty? urakat)
                           [{:teksti "Yhteensä" :luokka "lihavoitu"}
                            {:teksti (str (count urakat) " kpl urakoita") :luokka "lihavoitu"}
                            {:teksti ks-tilojen-yhteenveto :luokka "lihavoitu"}
                            {:teksti valikatselmus-tilojen-yhteenveto :luokka "lihavoitu"}
                            {:teksti lupaustietojen-yhteenveto :luokka "lihavoitu"}
                            {:teksti poikkeusten-yhteenveto :luokka "lihavoitu"}])))}
   [{:otsikko "Urakka"
     :tyyppi :string
     :nimi :nimi
     :leveys 8
     :muokattava? (constantly false)}

    {:otsikko "Hoito\u00ADvuosi"
     :muokattava? (constantly false)
     :nimi :hoitokauden_alkuvuosi
     :leveys 7
     :tyyppi :string
     :fmt #(pvm/hoitokausi-str-alkuvuodesta-vuodet %)}

    {:otsikko "Kustannus\u00ADsuunnitelma"
     :muokattava? (constantly false)
     :nimi :ks_tila
     :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [kustannussuunitelman-tila-sarake rivi])}

    {:otsikko "Väli\u00ADkatselmus"
     :muokattava? (constantly false)
     :nimi :ks_tila
     :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [valikatselmus-sarake rivi])}

    {:otsikko "Lupausten tavoite\u00ADpiste\u00ADmäärä"
     :muokattava? (constantly false)
     :nimi :ks_tila
     :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [lupauspisteet-sarake rivi])}

    {:otsikko "Avoimet poikkeamat"
     :muokattava? (constantly false)
     :nimi :ks_tila
     :leveys 15
     :tyyppi :komponentti
     :komponentti (fn [rivi] [avoimet-poikkeamat-sarake rivi])}]
   urakat])
