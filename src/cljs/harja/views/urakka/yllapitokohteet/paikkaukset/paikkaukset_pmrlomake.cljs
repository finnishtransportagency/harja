(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake
  (:require [harja.loki :refer [log]]
            [harja.domain.paikkaus :as paikkaus]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.validointi :as validointi]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake :as t-pmrlomake]))

(defn nimi-numero-ja-tp-kentat [lomake tyomenetelmat]
  [{:otsikko "Nimi"
    :tyyppi :string
    :nimi :nimi
    :pakollinen? true
    :vayla-tyyli? true
    :virhe? (validointi/nayta-virhe? [:nimi] lomake)
    :validoi [[:ei-tyhja "Anna nimi"]]
    ::lomake/col-luokka "col-sm-6"
    :pituus-max 100}
   {:otsikko "Numero"
    :tyyppi :numero
    :nimi :ulkoinen-id
    :virhe? (validointi/nayta-virhe? [:ulkoinen-id] lomake)
    :virheteksti (validointi/nayta-virhe-teksti [:ulkoinen-id] lomake)
    :vayla-tyyli? true
    :pakollinen? true
    ::lomake/col-luokka "col-sm-3"}
   {:otsikko "Työmenetelmä"
    :tyyppi :valinta
    :nimi :tyomenetelma
    :valinnat tyomenetelmat
    :valinta-arvo ::paikkaus/tyomenetelma-id
    :valinta-nayta ::paikkaus/tyomenetelma-nimi
    :vayla-tyyli? true
    :virhe? (validointi/nayta-virhe? [:tyomenetelma] lomake)
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
      :virhe? (validointi/nayta-virhe? [:tie] lomake)
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
      :virhe? (validointi/nayta-virhe? [:aosa] lomake)
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "A-et."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:aet] lomake)
      :nimi :aet}
     {:otsikko "L-osa."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:losa] lomake)
      :nimi :losa}
     {:otsikko "L-et."
      :tyyppi :numero
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:let] lomake)
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

(defn- pmr-skeema [lomake tyomenetelmat]
  (vec
    (concat (nimi-numero-ja-tp-kentat lomake tyomenetelmat)
            (sijainnin-kentat lomake)
            (lisatiedot-kentta))))

(defn pmr-lomake [_e! _lomake _tyomenetelmat]
  (fn [e! lomake tyomenetelmat]
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
                      [napit/tallenna
                       "Tallenna"
                       #(e! (t-pmrlomake/->TallennaPMRLomake (lomake/ilman-lomaketietoja lomake)))
                       {:disabled (not (::tila/validi? lomake))}]]
                     [:div.col-xs-4 {:style {:text-align "end"}}
                      [napit/yleinen-toissijainen "Sulje" #(e! (t-pmrlomake/->SuljePMRLomake))]]]])}
     (pmr-skeema lomake tyomenetelmat)
     lomake]))
