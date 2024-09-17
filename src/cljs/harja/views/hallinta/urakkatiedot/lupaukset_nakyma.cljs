(ns harja.views.hallinta.urakkatiedot.lupaukset-nakyma
  "Lupauksiin liittyvää hallinnointia esim. linkityksiä urakkaan"
  (:require [tuck.core :refer [tuck send-value! send-async!]]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader-pieni] :as yleiset]
            [harja.tiedot.hallinta.lupaukset-tiedot :as tiedot]
            [harja.ui.debug :as debug]))


(defn lupaukset* [e! app]
  (komp/luo
    (komp/sisaan #(do (e! (tiedot/->HaeLupaustenLinkitykset))
                    (e! (tiedot/->HaeLupaustenKategoriat))))
    (fn [e! {:keys [lupausten-linkitykset lupausten-kategoriat kategorian-urakat urakan-lupaukset valittu-kategoria valittu-urakka haku-kaynnissa?] :as app}]
      (let [puuttuvat-urakat (:puuttuvat-urakat lupausten-linkitykset)
            rivin-tunnistin-selitteet (:rivin-tunnistin-selitteet lupausten-kategoriat)
            kategorian-urakat (:kategorian-urakat kategorian-urakat)
            urakan-lupaukset (:urakan-lupaukset urakan-lupaukset)]
        [:div
         [:h2 "Lupauksien linkitys"]
         [:p "Lupaukset täytyy aina linkittää tiettyyn urakkaan. Tällä sivulla kerrotaan linkityksien tilanne ja on mahdollista tarkastella lupauksille syötettyjä tietoja."]
         (if (seq puuttuvat-urakat)
           [:div.alert.alert-danger
            [:p "Kehittäjän tulee korjata tilanne tekemällä linkki puutteellisille urakoille tai lupaukset eivät toimi näillä urakoilla."]
            [:p "Tällä hetkellä linkitykset puuttuvat seuraavissa urakoissa:"]
            [:ul
             (for [urakka puuttuvat-urakat]
               ^{:key (str "urakka" (:id urakka))}
               [:li (:nimi urakka)])]]
           [:div.alert.alert-success "Lupausten linkityksessä ei ole puutteita."])
         
         
         [:h2 "Lupauksien tarkistaminen"]
         [:p "Voit tarkistaa lupauksien linkitykset ja syötettyjä tietoja valitselmalla ensin kategorian ja sen jälkeen haluamasi urakan."]

          ;; Kategorian valinta
         [yleiset/pudotusvalikko
          "Lupaus kategoriat"
          {:valitse-fn #(e! (tiedot/->ValitseKategoria %))
           :valinta valittu-kategoria
           :format-fn #(cond
                         (and (:rivin-tunnistin-selite %) (:urakan-alkuvuosi %)) (str (:rivin-tunnistin-selite %) " - " (:urakan-alkuvuosi %))
                         (:urakan-alkuvuosi %) (:urakan-alkuvuosi %)
                         (:rivin-tunnistin-selite %) (:rivin-tunnistin-selite %)
                         :else "Valitse kategoria")}
          rivin-tunnistin-selitteet]

      
          ;; Urakan valinta
         (if haku-kaynnissa?
         [:div.ajax-loader-valistys
            [ajax-loader-pieni (str "Haetaan tietoja...")]]
         (when (seq kategorian-urakat)
           [yleiset/pudotusvalikko
            "Kategorian urakat"
            {:valitse-fn #(e! (tiedot/->ValitseUrakka %))
             :valinta valittu-urakka
             :format-fn #(or (:nimi %) "Valitse urakka")}
            kategorian-urakat]))

         ;; Lupaukset taulukko
         (when (seq urakan-lupaukset)
           [grid/grid
            {:otsikko "Lupaukset"
             :tyhja "Ei lupauksia."
             :tunniste :lupaus-id}
            [{:otsikko "Lupausryhmän numero"
              :nimi :lupausryhma-jarjestys
              :tyyppi :string
              :leveys 1}
             {:otsikko "Lupausryhmän otsikko"
              :nimi :otsikko
              :tyyppi :string
              :leveys 1}
             {:otsikko "Kuvaus"
              :nimi :kuvaus
              :tyyppi :string
              :leveys 1}
             {:otsikko "Sisalto"
              :nimi :sisalto
              :tyyppi :string
              :leveys 2}
             {:otsikko "Pisteet"
              :nimi :pisteet
              :tyyppi :string
              :leveys 1}
             {:otsikko "Lupaustyyppi"
              :nimi :lupaustyyppi
              :tyyppi :string
              :leveys 1}
             {:otsikko "Kirjauskuukaudet"
              :nimi :kirjaus-kkt
              :leveys 1
              :fmt str}
             {:otsikko "Jousto kuukaudet"
              :nimi :joustovara-kkta
              :tyyppi :string
              :leveys 1}
             {:otsikko "Päätöskuukausi"
              :nimi :paatos-kk
              :tyyppi :string
              :leveys 1}]
            urakan-lupaukset])]))))


(defn lupaukset []
  [tuck tiedot/tila lupaukset*])