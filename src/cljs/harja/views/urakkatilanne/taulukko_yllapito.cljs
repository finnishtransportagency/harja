(ns harja.views.urakkatilanne.taulukko-yllapito
  (:require [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.tiedot.urakkatilanne.kojelauta :as tiedot]
            [harja.views.urakkatilanne.sarakkeet.virheelliset :refer [virheelliset-tila-sarake]]))


(defn taulukko-paallystysurakat [_e! {:keys [urakat haku-kaynnissa?]}]
  [grid/grid
   {:otsikko (str "")
    :tyhja (if haku-kaynnissa?
             [ajax-loader "Ladataan tietoja"]
             "Ei tietoja, tarkistathan valitut suodattimet.")
    :luokat ["paallystysurakat"]
    :rivi-jalkeen-fn (fn [urakat]
                       (let [yhteenveto (tiedot/paallystystietojen-yhteenveto urakat)
                             valmiit-kohteet (tiedot/valmiit-yhteenveto urakat)
                             lahetetty (tiedot/lahetetyt-yhteenveto urakat)
                             valmiit-ei-lahetetty (tiedot/valmiit-ei-lahetetty-yhteenveto urakat)
                             epaonnistuneet-lahetetty (tiedot/epaonnistuneet-lahetetyt-yhteenveto urakat)
                             aloittamatta (tiedot/aloittamatta-yhteenveto urakat)]
                         (when-not (empty? urakat)
                           [{:teksti "Yhteensä" :luokka "lihavoitu"}
                            {:teksti (str (count urakat) " urak\u00ADkaa") :luokka "lihavoitu"}
                            {:teksti yhteenveto :luokka "lihavoitu"}
                            {:teksti aloittamatta :luokka "lihavoitu"}
                            {:teksti valmiit-ei-lahetetty :luokka "lihavoitu"}
                            {:teksti valmiit-kohteet :luokka "lihavoitu"}
                            {:teksti lahetetty :luokka "lihavoitu"}
                            {:teksti epaonnistuneet-lahetetty :luokka "lihavoitu"}
                            {:teksti ""}])))}
   [{:otsikko "Urakka"
     :tyyppi :string
     :nimi :nimi
     :leveys 7
     :muokattava? (constantly false)}

    {:otsikko "Vuosi"
     :muokattava? (constantly false)
     :nimi :hoitokauden_alkuvuosi
     :leveys 3
     :tyyppi :positiivinen-numero
     :kokonaisluku? true
     :tasaa :oikea}

    {:otsikko "Kohteiden lkm."
     :muokattava? (constantly false)
     :nimi :yllapitokohteiden_lkm
     :leveys 4
     :tyyppi :positiivinen-numero
     :kokonaisluku? true
     :tasaa :oikea}

    {:otsikko "Aloittamatta"
     :muokattava? (constantly false)
     :nimi :aloittamatta
     :leveys 6
     :tyyppi :positiivinen-numero
     :kokonaisluku? true
     :tasaa :oikea}

    {:otsikko "Valmiit, ei vielä lähetetty"
     :muokattava? (constantly false)
     :nimi :valmiit_ei_lahetetty
     :leveys 6
     :tyyppi :positiivinen-numero
     :kokonaisluku? true
     :tasaa :oikea}

    {:otsikko "Valmis/hyväksytty"
     :muokattava? (constantly false)
     :nimi :valmis_hyvaksytty
     :leveys 6
     :tyyppi :positiivinen-numero
     :kokonaisluku? true
     :tasaa :oikea}

    {:otsikko "Lähetetty onnistuneesti YHA:an"
     :muokattava? (constantly false)
     :nimi :lahetetty_onnistuneesti
     :leveys 6
     :tyyppi :positiivinen-numero
     :kokonaisluku? true
     :tasaa :oikea}

    {:otsikko "Epäonnistu\u00ADneet YHA-lähetykset"
     :muokattava? (constantly false)
     :nimi :epaonnistuneet_lahetetyt
     :leveys 6
     :tyyppi :positiivinen-numero
     :kokonaisluku? true
     :tasaa :oikea}

    {:otsikko "Kohteet, joissa lähetys\u00ADvirhe"
     :muokattava? (constantly false)
     :nimi :virheelliset_kohteet
     :leveys 6
     :tyyppi :komponentti
     :komponentti (fn [rivi] (virheelliset-tila-sarake rivi))}]
   urakat])
