(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake
  (:require [harja.loki :refer [log]]
            [harja.domain.paikkaus :as paikkaus]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake :as t-pmrlomake]))

(defn nayta-virhe? [polku lomake]
  (let [validi? (if (nil? (get-in lomake polku))
                  true ;; kokeillaan palauttaa true, jos se on vaan tyhjä. Eli ei näytetä virhettä tyhjälle kentälle
                  (get-in lomake [::tila/validius polku :validi?]))]
    ;; Koska me pohjimmiltaan tarkistetaan, validiutta, mutta palautetaan tieto, että näytetäänkö virhe, niin käännetään
    ;; boolean ympäri
    (not validi?)))

(defn nimi-numero-ja-tp-kentat [lomake]
  [{:otsikko "Nimi"
    :tyyppi :string
    :nimi :nimi
    :pakollinen? true
    :vayla-tyyli? true
    :virhe? (nayta-virhe? [:nimi] lomake)
    :validoi [[:ei-tyhja "Anna nimi"]]
    ::lomake/col-luokka "col-sm-6"
    :pituus-max 100}
   {:otsikko "Lask.nro"
    :tyyppi :string
    :nimi :nro
    :virhe? (nayta-virhe? [:nro] lomake)
    ;:validoi [[:ei-tyhja "Anna laskunumero"]]
    :vayla-tyyli? true
    :pakollinen? true
    ::lomake/col-luokka "col-sm-3"}
   {:otsikko "Työmenetelmä"
    :tyyppi :valinta
    :nimi :tyomenetelma
    :valinnat paikkaus/paikkauskohteiden-tyomenetelmat
    :valinta-nayta paikkaus/kuvaile-tyomenetelma
    :vayla-tyyli? true
    :virhe? (nayta-virhe? [:tyomenetelma] lomake)
    :pakollinen? true
    ::lomake/col-luokka "col-sm-12"
    :muokattava? #(not (or (= "tilattu" (:paikkauskohteen-tila lomake))
                           (= "valmis" (:paikkauskohteen-tila lomake))))}])

(defn sijainnin-kentat [lomake]
  [(lomake/ryhma
     {:otsikko "Sijainti"
      :rivi? true
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}

     {:otsikko "Tie"
      :tyyppi :numero
      :nimi :tie
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:tie] lomake)
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "Ajorata"
      :tyyppi :valinta
      :valinnat {nil "Ei ajorataa"
                 0 0
                 1 1
                 2 2}
      :valinta-arvo first
      :valinta-nayta second
      :nimi :ajorata
      :vayla-tyyli? true
      :pakollinen? false})
   (lomake/rivi
     {:otsikko "A-osa"
      :tyyppi :numero
      :pakollinen? true
      :nimi :aosa
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:aosa] lomake)
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "A-et."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:aet] lomake)
      :nimi :aet}
     {:otsikko "L-osa."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:losa] lomake)
      :nimi :losa}
     {:otsikko "L-et."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:let] lomake)
      :nimi :let}
     {:otsikko "Pituus (m)"
      :tyyppi :numero
      :vayla-tyyli? true
      :disabled? true
      :nimi :pituus
      :tarkkaile-ulkopuolisia-muutoksia? true
      :muokattava? (constantly false)})])

(defn- lisatiedot-kentta []
  [(lomake/rivi
     {:otsikko "Lisätiedot"
      :tyyppi :text
      :nimi :lisatiedot
      :pakollinen? false
      :uusi-rivi? true
      ::lomake/col-luokka "col-sm-12"})])

(defn- pmr-skeema [lomake]
  (vec
    (concat (nimi-numero-ja-tp-kentat lomake)
            (sijainnin-kentat lomake)
            (lisatiedot-kentta))))

(defn pmr-lomake [_e! _lomake]
  (fn [e! lomake]
    [lomake/lomake
     {:ei-borderia? true
      :tarkkaile-ulkopuolisia-muutoksia? true
      :otsikko "Muokkaa paikkauskohdetta"
      :muokkaa! #(e! (t-pmrlomake/->PaivitaPMRLomake (lomake/ilman-lomaketietoja %)))
      :footer-fn (fn [lomake]
                   [:div.row
                    [:hr]
                    [:div.row
                     [:div.col-xs-8 {:style {:padding-left "0"}}
                      [napit/tallenna "Tallenna" #(e! (t-pmrlomake/->TallennaPMRLomake (lomake/ilman-lomaketietoja lomake)))]]
                     [:div.col-xs-4 {:style {:text-align "end"}}
                      [napit/yleinen-toissijainen "Sulje" #(e! (t-pmrlomake/->SuljePMRLomake))]]]])}
     (pmr-skeema lomake)
     lomake]))
