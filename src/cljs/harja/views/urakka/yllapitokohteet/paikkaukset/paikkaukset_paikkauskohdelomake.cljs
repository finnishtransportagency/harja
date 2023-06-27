(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake
  (:require [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.debug :as debug]
            [harja.ui.lomake :as lomake]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.viesti :as viesti]
            [harja.ui.validointi :as validointi]
            [harja.ui.sivupalkki :as sivupalkki]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as t-toteumalomake]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as v-toteumalomake]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake :as v-pmrlomake]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.domain.tierekisteri :as tr-domain])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- viesti-tiemerkintaan-modal [e! lomake tiemerkintaurakat tyomenetelmat]
  (let [voi-lahettaa? (::tila/validi? lomake)]
    [modal/modal
     {:otsikko (str "Viesti tiemerkintaan")
      :nakyvissa? true
      :sulje-fn #(e! (t-paikkauskohteet/->SuljeTiemerkintaModal))
      :footer [:div.row
               [:div.col-xs-6 {:style {:text-align "left"}}
                [napit/palvelinkutsu-nappi
                 "Lähetä viesti"
                 #(t-paikkauskohteet/tallenna-tilamuutos! (lomake/ilman-lomaketietoja (assoc lomake :paikkauskohteen-tila "valmis")))
                 {:disabled (not voi-lahettaa?)
                  :ikoni (ikonit/check)
                  :kun-onnistuu (fn [vastaus]
                                  ;; Merkkaa valmiiksi tässä
                                  (e! (t-paikkauskohteet/->MerkitsePaikkauskohdeValmiiksiOnnistui vastaus))
                                  (viesti/nayta-toast! "Tiemerkintää informoitu onnistuneesti!" :onnistui viesti/viestin-nayttoaika-keskipitka))
                  :kun-virhe (fn [vastaus]
                               (e! (t-paikkauskohteet/->MerkitsePaikkauskohdeValmiiksiEpaonnistui vastaus)))}]]
               [:div.col-xs-6 {:style {:text-align "end"}}
                [napit/peruuta
                 "Kumoa"
                 #(e! (t-paikkauskohteet/->SuljeTiemerkintaModal))]]]}

     [:div
      [lomake/lomake {:ei-borderia? true
                      :muokkaa! #(e! (t-paikkauskohteet/->PaivitaTiemerkintaModal %))}
       [{:otsikko "Tiemerkinnän suorittava urakka"
         :nimi :tiemerkinta-urakka
         :tyyppi :valinta
         :vayla-tyyli? true
         :valinnat tiemerkintaurakat
         :valinta-arvo :id
         :valinta-nayta :nimi
         :pakollinen? true
         ::lomake/col-luokka "col-xs-12"}
        {:nimi :teksti
         :tyyppi :komponentti
         :komponentti (fn [] [:span (str "Kohteen ")
                              [:strong (:paikkauskohde-nimi lomake)]
                              (str " paikkaustyö on valmistunut")])
         ::lomake/col-luokka "col-xs-12"}
        {:nimi :tyomenetelma
         :tyyppi :string
         :otsikko "Työmenetelmä"
         :muokattava? (constantly false)
         :fmt #(paikkaus/tyomenetelma-id->nimi % tyomenetelmat)
         ::lomake/col-luokka "col-xs-12"}
        {:nimi :sijainti
         :otsikko "Sijainti"
         :tyyppi :komponentti
         :komponentti (fn [d]
                        (let [data (:data d)]
                          [:span (str (:tie data) " " (:aosa data) "/" (:aet data) " - " (:losa data) "/" (:let data))]))
         ::lomake/col-luokka "col-xs-12"}
        {:nimi :pituus
         :otsikko "Pituus"
         :tyyppi :komponentti
         :komponentti (fn [d]
                        (let [data (:data d)]
                          [:span (:pituus data) " m"]))
         ::lomake/col-luokka "col-xs-12"}
        (lomake/ryhma
          {:otsikko "TIEMERKINTÖJEN TILANNE"
           :rivi? true
           :ryhman-luokka "lomakeryhman-otsikko-tausta"}
          {:nimi :infoteksti
           :tyyppi :komponentti

           :rivi-luokka "lomakeryhman-rivi-tausta"
           :komponentti (fn []
                          [:div "Arvioi " [:strong "kuinka monta metriä "] " reunaviivaa, keskiviivaa ja pienmerkintöjä on tuhoutunut."])
           ::lomake/col-luokka "col-xs-12"}
          {:nimi :viesti
           :otsikko "Viestisi tiemerkintään"
           :tyyppi :text
           :koko [90 7]
           :pakollinen? true
           ::lomake/col-luokka "col-xs-12"
           :rivi-luokka "lomakeryhman-rivi-tausta"})
        {:teksti "Lähetä sähköpostiini kopio viestistä"
         :nimi :kopio-itselle?
         ::lomake/col-luokka "col-xs-12"
         :tyyppi :checkbox}]
       lomake]]]))

(defn lukutila-rivi [otsikko arvo]
  [:div {:style {:padding-top "16px" :padding-bottom "8px"}}
   [:div.row
    [:span {:style {:font-weight 400 :font-size "12px" :color "#5C5C5C"}} otsikko]]
   [:div.row
    [:span {:style {:font-weight 400 :font-size "14px" :line-height "20px" :color "black"}} arvo]]])

(defn suunnitelman-kentat [lomake]
  [(lomake/ryhma
     {:otsikko "Alustava suunnitelma"
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}
     {:otsikko "Arv. aloitus"
      :tyyppi :pvm
      :nimi :alkupvm
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:alkupvm] lomake)
      ::lomake/col-luokka "col-sm-6"}
     {:otsikko "Arv. lopetus"
      :tyyppi :pvm
      :nimi :loppupvm
      :pakollinen? true
      :vayla-tyyli? true
      :pvm-tyhjana #(:alkupvm %)
      :rivi lomake
      :virhe? (validointi/nayta-virhe? [:loppupvm] lomake)
      ::lomake/col-luokka "col-sm-6"})
   (lomake/rivi
     {:otsikko "Suunniteltu määrä"
      :tyyppi :positiivinen-numero
      :nimi :suunniteltu-maara
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:suunniteltu-maara] lomake)
      ::lomake/col-luokka "col-sm-4"
      :rivi-luokka "lomakeryhman-rivi-tausta"
      :disabled? (when (= "tilattu" (:paikkauskohteen-tila lomake))
                   true)}
     {:otsikko "Yksikkö"
      :tyyppi :valinta
      :valinnat (vec paikkaus/paikkauskohteiden-yksikot)
      :nimi :yksikko
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:yksikko] lomake)
      ::lomake/col-luokka "col-sm-2"
      :disabled? (when (= "tilattu" (:paikkauskohteen-tila lomake))
                   true)}
     {:otsikko "Suunniteltu hinta"
      :tyyppi :positiivinen-numero
      :desimaalien-maara 2
      :piilota-yksikko-otsikossa? true
      :nimi :suunniteltu-hinta
      ::lomake/col-luokka "col-sm-6"
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:suunniteltu-hinta] lomake)
      :yksikko "€"
      :disabled? (when (= "tilattu" (:paikkauskohteen-tila lomake))
                   true)})
   (lomake/rivi
     {:otsikko "Lisätiedot"
      :tyyppi :text
      :nimi :lisatiedot
      :pakollinen? false
      ::lomake/col-luokka "col-sm-12"
      :rivi-luokka "lomakeryhman-rivi-tausta"})])

(defn sijainnin-kentat [lomake]
  [(lomake/ryhma
     {:otsikko "Sijainti"
      :rivi? true
      :ryhman-luokka "lomakeryhman-otsikko-tausta"}

     {:otsikko "Tie"
      :tyyppi :positiivinen-numero
      :kokonaisluku? true
      :nimi :tie
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:tie] lomake)
      :virheteksti (validointi/nayta-virhe-teksti [:tie] lomake)
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
      :virheteksti (validointi/nayta-virhe-teksti [:ajorata] lomake)
      :vayla-tyyli? true
      :pakollinen? false})
   (lomake/rivi
     {:otsikko "A-osa"
      :tyyppi :positiivinen-numero
      :kokonaisluku? true
      :pakollinen? true
      :nimi :aosa
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:aosa] lomake)
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     {:otsikko "A-et."
      :tyyppi :positiivinen-numero
      :kokonaisluku? true
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:aet] lomake)
      :nimi :aet}
     {:otsikko "L-osa."
      :tyyppi :positiivinen-numero
      :kokonaisluku? true
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (validointi/nayta-virhe? [:losa] lomake)
      :nimi :losa}
     {:otsikko "L-et."
      :tyyppi :positiivinen-numero
      :kokonaisluku? true
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

(defn nimi-numero-ja-tp-kentat [lomake tyomenetelmat]
  [{:otsikko "Nimi"
    :elementin-id "form-paikkauskohde-nimi"
    :tyyppi :string
    :nimi :nimi
    :pakollinen? true
    :vayla-tyyli? true
    :virhe? (validointi/nayta-virhe? [:nimi] lomake)
    :virheteksti (validointi/nayta-virhe-teksti [:nimi] lomake)
    :validoi [[:ei-tyhja "Anna nimi"]]
    ::lomake/col-luokka "col-sm-6"
    :pituus-max 100}
   {:otsikko "Numero"
    :tyyppi :positiivinen-numero
    :kokonaisluku? true
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

(defn- raportoinnin-kentat [e! lomake toteumalomake voi-muokata? tyomenetelmat]
  (let [urakoitsija? (t-paikkauskohteet/kayttaja-on-urakoitsija? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)))
        tilaaja? (roolit/kayttaja-on-laajasti-ottaen-tilaaja?
                   (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
                   @istunto/kayttaja)
        valmis? (= "valmis" (:paikkauskohteen-tila lomake))
        urem? (= "UREM" (paikkaus/tyomenetelma-id->lyhenne (:tyomenetelma lomake) tyomenetelmat))
        pot-raportoitava? (= :pot (:toteumatyyppi lomake))
        toteutunut-hinta (:toteutunut-hinta lomake)
        suunniteltu-hinta (:suunniteltu-hinta lomake)
        erotus (if toteutunut-hinta
                 (- suunniteltu-hinta toteutunut-hinta)
                 0)
        toteutunut-maara-avain-yksikosta #(cond
                                            (= "t" %) :toteutunut-massamaara
                                            (= "kpl" %) :toteutunut-kpl
                                            (= "m2" %) :toteutunut-pinta-ala
                                            (= "jm" %) :toteutunut-juoksumetri
                                            :default :toteutunut-massamenekki)
        ;; Pot raportoitava paikkauskohde voidaan merkata valmiiksi, vaikka itse pot ei olisi valmis.
        ;; Pot lomakkeella valmistumispäivämäärä ei tästä syystä ole pakollinen, mutta tällä lomakkeella se on
        ;; Joten otetaan se työ päättyi päivästä
        lomake (if (and pot-raportoitava? (:paikkaustyo-valmis? lomake) (nil? (:valmistumispvm lomake)))
                 (assoc lomake :valmistumispvm (:pot-tyo-paattyi lomake))
                 lomake)]
    [(lomake/ryhma
       {:otsikko "Arvioitu aikataulu"
        :ryhman-luokka "lomakeryhman-otsikko-tausta"}
       (when voi-muokata?
         {:otsikko "Arv. aloitus"
          :tyyppi :pvm
          :ikoni-sisaan? true
          :nimi :alkupvm
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (validointi/nayta-virhe? [:alkupvm] lomake)
          ::lomake/col-luokka "col-sm-6"})
       (when voi-muokata?
         {:otsikko "Arv. lopetus"
          :tyyppi :pvm
          :nimi :loppupvm
          :ikoni-sisaan? true
          :pakollinen? true
          :vayla-tyyli? true
          :pvm-tyhjana #(:alkupvm %)
          :rivi lomake
          :virhe? (validointi/nayta-virhe? [:loppupvm] lomake)
          ::lomake/col-luokka "col-sm-6"})
       (when (not voi-muokata?)
         {:tyyppi :string
          :piilota-label? true
          :nimi :aikataulu
          :hae #(str (pvm/paiva-kuukausi (:alkupvm %)) "-" (pvm/pvm (:loppupvm %)))}))

     (lomake/ryhma
       (merge {:otsikko "Paikkaustyö"
               :ryhman-luokka "lomakeryhman-otsikko-tausta"}
              ;; Urakoitsijat ja järjestelmävalvojat voivat lisätä toteumia, jos seuraavat ehdot täyttyvät:
              ;; työmenetelmä ei ole UREM
              ;; Raportointitapa ei ole pot
              ;; Urakka on "tilattu" tilassa
              ;; Ja käyttäjällä on oikeudet lisätä toteumia (urakoitsija tai järjestelmävastaava)
              (when (and (= :normaali (:toteumatyyppi lomake))
                         (= "tilattu" (:paikkauskohteen-tila lomake))
                         (not urem?)
                         (or urakoitsija? tilaaja?))
                {:nappi [napit/yleinen-toissijainen "Lisää toteuma"
                         #(e! (t-toteumalomake/->AvaaToteumaLomake (assoc toteumalomake :tyyppi :uusi-toteuma) lomake))
                         {:paksu? true
                          :ikoni (ikonit/livicon-plus)}]})
              ;; POT raportointia varten tarvitaan erilainen nappi. Jotta se voidaan näyttää, paikkauskohteen täytyy olla
              ;; pot-raportoitava, sekä tietenkin kaikki normaali toteumanappia varten tehdyt ehdot pitää täyttyä
              (when (and pot-raportoitava?
                         (not (nil? (:yllapitokohde-id lomake)))
                         (or (= "tilattu" (:paikkauskohteen-tila lomake)) (= "valmis" (:paikkauskohteen-tila lomake)))
                         (or urakoitsija? tilaaja?))
                {:nappi [napit/yleinen-toissijainen (if (not (nil? (:pot-tila lomake))) ;; Tilavaihtoehdot: aloitettu, valmis, lukittu
                                                      "Avaa päällystysilmoitus"
                                                      "Tee päällystysilmoitus")
                         #(siirtymat/avaa-paikkausten-pot! {:paallystyskohde-id (:yllapitokohde-id lomake)
                                                            :paallystysilmoitus-id (:pot-id lomake)
                                                            :kohteen-urakka-id (:urakka-id lomake)
                                                            :valittu-urakka-id (:id @nav/valittu-urakka)})
                         {:paksu? true
                          :ikoni (ikonit/livicon-plus)}]}))

       ;; Kun työmenetelmänä on UREM niin näytetään ilmoitus, että toteumat tulee vain rajapinnan kautta
       (when urem?
         (lomake/rivi
           {:nimi :urem-alert
            :tyyppi :komponentti
            :komponentti (fn []
                           [harja.ui.yleiset/varoitus-vihje
                            "Urapaikkauksen toteumat voi tuoda Harjan rajapinnan tai Excel-tiedoston avulla" nil])
            ::lomake/col-luokka "col-xs-12"
            :rivi-luokka "lomakeryhman-rivi-tausta"}))
       ;; Kun POT raportoitava, niin näytä potin tila
       (when (and pot-raportoitava?
                  (not (nil? (:yllapitokohde-id lomake)))
                  (or urakoitsija? tilaaja?))
         (lomake/rivi
           {:otsikko "Päällystysilmoitus"
            :nimi :pot-tila
            :tyyppi :string
            :fmt (fn [arvo]
                   (cond
                     (nil? arvo) "Aloittamatta"
                     (= "aloitettu" arvo) "Aloitettu"
                     (= "valmis" arvo) "Valmis käsiteltäväksi"
                     (and (= "lukittu" arvo) (= "hyvaksytty" (:pot-paatos lomake))) "Hyväksytty"
                     (and (= "lukittu" arvo) (= "hylatty" (:pot-paatos lomake))) "Hylatty"
                     :else "Aloittamatta"))
            :muokattava? (constantly false)
            ::lomake/col-luokka "col-xs-12"
            :rivi-luokka "lomakeryhman-rivi-tausta"}))

       ;; Lukutilassa, kun toteumilla raportoitava paikkauskohde, näytetään tekstinä toteutusaika ja takuuaika
       (when (not voi-muokata?) ;(not pot-raportoitava?) (and pot-raportoitava? valmis?)                  
         (lomake/rivi
           {:otsikko "Toteutusaika"
            :tyyppi :string
            :nimi :toteutusaika
            :muokattava? (constantly false)
            ;; Näytetään vain alkuaika, jos paikkauskohde ei ole vielä valmis ja loppuaika lisätään jos se on valmistunut
            :hae #(let [aloitusaika (:toteutus-alkuaika %)
                        lopetusaika (:toteutus-loppuaika %)]
                    (cond
                      (and (not pot-raportoitava?) aloitusaika lopetusaika)
                      (str (pvm/paiva-kuukausi aloitusaika) " - " (pvm/pvm lopetusaika))
                      (and pot-raportoitava? (:pot-tyo-alkoi %) (:pot-tyo-paattyi %))
                      (str (pvm/paiva-kuukausi (:pot-tyo-alkoi %)) " - " (pvm/pvm (:pot-tyo-paattyi %)))
                      :oletus "–"))
            :rivi-luokka "lomakeryhman-rivi-tausta"
            ::lomake/col-luokka "col-sm-4"}
           {:otsikko "Valmistumispäivä"
            :tyyppi :pvm
            :nimi (if pot-raportoitava?
                    :pot-valmistumispvm
                    :valmistumispvm) ;; Tarkista, kunhan tietomalli päivitetty
            :muokattava? (constantly false)
            :jos-tyhja "–"
            ::lomake/col-luokka "col-sm-4"}
           {:otsikko "Takuuaika"
            :tyyppi :string
            :nimi :takuuaika
            :muokattava? (constantly false)
            :fmt #(if (nil? %) "–" (str % " vuotta"))
            ::lomake/col-luokka "col-sm-4"}))

       (when-not pot-raportoitava?
         (lomake/rivi
           {:otsikko "Suunniteltu määrä"
            :tyyppi :string
            :nimi :suunniteltu-maara-ja-yksikko
            :muokattava? (constantly false)
            :hae #(str (:suunniteltu-maara %) " " (:yksikko %))
            :uusi-rivi? true
            :rivi-luokka "lomakeryhman-rivi-tausta"
            ::lomake/col-luokka "col-sm-4"}
           {:otsikko "Toteutunut määrä"
            :tyyppi :string
            :nimi :toteutunut-maara-ja-yksikko
            :hae #(str ((toteutunut-maara-avain-yksikosta (:yksikko lomake)) %) " " (:yksikko %))
            ;:yksikko (:yksikko lomake)
            ;:vayla-tyyli? true
            :muokattava? (constantly false)
            :jos-tyhja "–"
            ::lomake/col-luokka "col-sm-4"}
           {:otsikko "Kirjatut toteumat"
            :nimi :toteumien-maara
            :tyyppi :komponentti
            :komponentti (fn []
                           (if (> (:toteumien-maara lomake) 0)
                             [:a
                              {:href "#"
                               :on-click (fn [e]
                                           (do
                                             (.preventDefault e)
                                             (siirtymat/avaa-toteuma-listaus! {:urakka-id (:id @nav/valittu-urakka)
                                                                               :paikkauskohde-id (:id lomake)})))}
                              (str (:toteumien-maara lomake))]
                             [:div "0"]))
            ::lomake/col-luokka "col-xs-12"
            :rivi-luokka "lomakeryhman-rivi-tausta"}))


       (when (and voi-muokata? (or urakoitsija? tilaaja?))
         {:teksti "Paikkaustyö on valmis"
          :nimi :paikkaustyo-valmis?
          :tyyppi :checkbox
          :vayla-tyyli? true

          :disabled? (or
                       ;; Checkbox on mahdollisesti disabloitu. POT-raportoitavassa POT-lomake tarvitsee vain aloittaa
                       ;; Mutta se ei myöskään saa olla hylätty
                       (and
                         pot-raportoitava?
                         (not= (nil? (:pot-tila lomake)))
                         (not= "hylatty" (:pot-tila lomake)))
                       ;; Checkbox on disabloitu mikäli toteumilla raportoidun paikkauskohteen
                       ;; tila on tilattu ja toteumien määrä on nolla
                       (and
                         (not pot-raportoitava?)
                         (= "tilattu" (:paikkauskohteen-tila lomake))
                         (= 0 (:toteumien-maara lomake))))
          ::lomake/col-luokka "col-sm-12"
          :rivi-luokka "lomakeryhman-rivi-tausta"
          :vihje (when (not pot-raportoitava?)
                   "Kohteen voi merkitä valmiiksi, kun sillä on toteumia.")})
       ;; Toteumilla raportoitava paikkauskohde sisältää valmiiksimerkitsemisvaiheessa valmistumispäivämäärän ja takuuajan
       (when (and (not pot-raportoitava?) (not valmis?) (:paikkaustyo-valmis? lomake)
                  (<= 1 (:toteumien-maara lomake)) voi-muokata? (or urakoitsija? tilaaja?))
         (lomake/rivi
           {:otsikko "Valmistumispvm"
            :tyyppi :pvm
            :nimi :valiaika-valmistumispvm
            :vayla-tyyli? true
            ;:virhe? (validointi/nayta-virhe? [:valiaika-valmistumispvm] lomake)
            :rivi-luokka "lomakeryhman-rivi-tausta"
            ::lomake/col-luokka "col-sm-6"}
           {:otsikko "Takuuaika"
            :tyyppi :valinta
            :valinnat {0 "Ei takuuaikaa"
                       1 "1 vuosi"
                       2 "2 vuotta"
                       3 "3 vuotta"}
            :valinta-arvo first
            :valinta-nayta second
            :nimi :valiaika-takuuaika
            :vayla-tyyli? true
            ::lomake/col-luokka "col-sm-6"}))

       ;; Pot raportoitava paikkaukkauskohde voidaan merkitä valmiiksi, vaikka itse POT-lomake olisi kesken.
       ;; Muita ehtoja kuitenkin on oltava useampia
       (when (and pot-raportoitava? (not valmis?) (:paikkaustyo-valmis? lomake)
               voi-muokata? (or urakoitsija? tilaaja?))
         (lomake/rivi
           {:otsikko "Työ alkoi"
            :tyyppi :pvm
            :ikoni-sisaan? true
            :nimi :pot-tyo-alkoi
            :vayla-tyyli? true
            :virhe? (validointi/nayta-virhe? [:pot-tyo-alkoi] lomake)
            ::lomake/col-luokka "col-sm-5"
            :rivi-luokka "lomakeryhman-rivi-tausta"
            :pakollinen? true}
           {:otsikko "Työ päättyi"
            :tyyppi :pvm
            :ikoni-sisaan? true
            :nimi :pot-tyo-paattyi
            :vayla-tyyli? true
            :virhe? (validointi/nayta-virhe? [:pot-tyo-paattyi] lomake)
            :rivi-luokka "lomakeryhman-rivi-tausta"
            ::lomake/col-luokka "col-sm-7"
            :pakollinen? true}))
       ;; Pot raportoitava paikkaukkauskohde voidaan merkitä valmiiksi, vaikka itse POT-lomake olisi kesken.
       ;; Muita ehtoja kuitenkin on oltava useampia
       (when (and pot-raportoitava? (not valmis?) (:paikkaustyo-valmis? lomake)
               voi-muokata? (or urakoitsija? tilaaja?))
         (lomake/rivi
           {:otsikko "Valmistumispvm"
            :tyyppi :pvm
            :nimi :pot-valmistumispvm
            :ikoni-sisaan? true
            :vayla-tyyli? true
            :virhe? (validointi/nayta-virhe? [:pot-valmistumispvm] lomake)
            :virheteksti (validointi/nayta-virhe-teksti [:pot-valmistumispvm] lomake)
            ::lomake/col-luokka "col-sm-5"
            :rivi-luokka "lomakeryhman-rivi-tausta"
            :pakollinen? true}
           {:otsikko "Takuuaika"
            :tyyppi :valinta
            :valinnat {0 "Ei takuuaikaa"
                       1 "1 vuosi"
                       2 "2 vuotta"
                       3 "3 vuotta"}
            :valinta-arvo first
            :valinta-nayta second
            :nimi :valiaika-takuuaika
            :vayla-tyyli? true
            ::lomake/col-luokka "col-sm-4"
            :rivi-luokka "lomakeryhman-rivi-tausta"
            :pakollinen? true}))

       (when (and voi-muokata? (or urakoitsija? tilaaja?))
         (merge {:teksti "Tiemerkintää tuhoutunut"
                 :nimi :tiemerkintaa-tuhoutunut?
                 :vayla-tyyli? true
                 :tyyppi :checkbox
                 :uusi-rivi? true
                 ;; Tiemerkintä checkbox on disabled mikäli paikkaustyö ei ole vielä valmis
                 ;; tai jos tiemerkintään on jo lähetetty viesti (eli lähetyspäivämäärä löytyy)
                 :disabled? (or (not (:paikkaustyo-valmis? lomake))
                                (not (nil? (:tiemerkintapvm lomake))))
                 ::lomake/col-luokka "col-sm-12"
                 :rivi-luokka "lomakeryhman-rivi-tausta"}
                ;; Jos tiemerkintään on lähetetty jo viesti, niin turha ohjeistaa enää käyttäjää
                (when (nil? (:tiemerkintapvm lomake))
                  {:vihje "Kirjoita viesti tiemerkinnälle tallennuksen yhteydessä"})))

       ;; Komponentti tiemerkintätuhoutunut timestampin näyttämiseksi
       (when (:tiemerkintapvm lomake)
         {:nimi :tiemerkinta-alert
          :tyyppi :komponentti
          :komponentti (fn []
                         [harja.ui.yleiset/varoitus-vihje
                          "Tiemerkintää tuhoutunut "
                          (str "Viesti tiemerkinnälle lähetetty " (pvm/pvm-aika-klo (:tiemerkintapvm lomake)))])
          ::lomake/col-luokka "col-xs-12"
          :rivi-luokka "lomakeryhman-rivi-tausta"}))

     (lomake/ryhma
       {:otsikko "Kustannukset"
        :ryhman-luokka "lomakeryhman-otsikko-tausta"}
       (lomake/rivi
         {:otsikko "Suunniteltu hinta"
          :tyyppi :positiivinen-numero
          :nimi :suunniteltu-hinta
          :muokattava? (constantly false)
          :fmt #(if (some? %) (fmt/euro %) "-")
          :rivi-luokka "lomakeryhman-rivi-tausta"
          ::lomake/col-luokka "col-sm-4"}
         {:otsikko "Toteutunut hinta"
          :tyyppi :positiivinen-numero
          :fmt #(if (some? %) (fmt/euro %) "-")
          :nimi :toteutunut-hinta
          ::lomake/col-luokka "col-sm-4"}
         {:otsikko "Erotus"
          :tyyppi :string
          :nimi :erotus
          :muokattava? (constantly false)
          :kentan-arvon-luokka (condp apply [0 erotus]
                                 = "harmaa-tumma-teksti"
                                 < "vihrea-teksti"
                                 > "punainen-teksti"
                                 nil)
          :hae #(if (and (:toteutunut-hinta %) (:suunniteltu-hinta %))
                  (str (fmt/euro false (- (:toteutunut-hinta %) (:suunniteltu-hinta %))) " €")
                  "–")
          ::lomake/col-luokka "col-sm-4"}))]))

(defn paikkauskohde-skeema [e! voi-muokata? raportointitila? lomake toteumalomake tyomenetelmat]
  (let [nimi-nro-ja-tp (when voi-muokata?
                         (nimi-numero-ja-tp-kentat lomake tyomenetelmat))
        sijainti (when voi-muokata?
                   (sijainnin-kentat lomake))
        suunnitelma (when voi-muokata?
                      (suunnitelman-kentat lomake))
        raportointi (when raportointitila? (raportoinnin-kentat e! lomake toteumalomake voi-muokata? tyomenetelmat))]
    (vec
      (if raportointitila?
        raportointi
        (concat nimi-nro-ja-tp
                sijainti
                suunnitelma)))))

(defn- nayta-pot-valinta?
  " Tilaajalle näytetään kolmen työmenetelmän kohdalla erillinen pot/toteuma radiobutton valinta.
  Mikäli tilaaja valitsee pot vaihtoehdon, toteumia ei kirjata normaaliprossin mukaan, vaan pot-lomakkeelta
  Kolme työmenetelmää ovat: AB-paikkaus levittäjällä, PAB-paikkaus levittäjällä, SMA-paikkaus levittäjällä"
  [lomake tyomenetelmat]
  (let [
        nayta? (and (roolit/kayttaja-on-laajasti-ottaen-tilaaja?
                      (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
                      @istunto/kayttaja)
                    (= "ehdotettu" (:paikkauskohteen-tila lomake))
                    (paikkaus/levittimella-tehty? lomake tyomenetelmat))]
    nayta?))

(defn- lomake-otsikko [lomake]
  [:<>
   [:div {:style {:padding-left "16px" :padding-top "32px"}}
    [:div.pieni-teksti (:ulkoinen-id lomake)]
    [:h2 (:nimi lomake)]]

   (if (:paikkauskohteen-tila lomake)
     [:div.row.margin-top-16
      [:div.col-xs-12
       [:div {:class ["tila-bg" (:paikkauskohteen-tila lomake) "lomakkeella"]
              :style {:display "inline-block"}}
        [:div
         [:div {:class (str "circle "
                            (cond
                              (= "tilattu" (:paikkauskohteen-tila lomake)) "tila-tilattu"
                              (= "ehdotettu" (:paikkauskohteen-tila lomake)) "tila-ehdotettu"
                              (= "valmis" (:paikkauskohteen-tila lomake)) "tila-valmis"
                              (= "hylatty" (:paikkauskohteen-tila lomake)) "tila-hylatty"
                              :default "tila-ehdotettu"))}]
         [:span (paikkaus/fmt-tila (:paikkauskohteen-tila lomake))]]]
       (when (:tilattupvm lomake)
         [:span.pieni-teksti {:style {:padding-left "24px"
                                      :display "inline-block"}}
          (str "Tilauspvm " (harja.fmt/pvm (:tilattupvm lomake)))])
       [:span.pieni-teksti {:style {:padding-left "24px"
                                    :display "inline-block"}}
        (if (:muokattu lomake)
          (str "Päivitetty " (harja.fmt/pvm (:muokattu lomake)))
          "Ei päivitystietoa")]]]
     [:span "Tila ei tiedossa"])])

(defn- lomake-lukutila [e! lomake nayta-muokkaus? tyomenetelmat]
  [:div
   [lomake-otsikko lomake]
   ;; Jos kohde on hylätty, urakoitsija ei voi muokata sitä enää.
   (when nayta-muokkaus?
     [:div.col-xs-12 {:style {:padding-top "24px"}}
      [napit/muokkaa "Muokkaa kohdetta" #(e! (t-paikkauskohteet/->AvaaLomake (assoc lomake
                                                                               :tyyppi :paikkauskohteen-muokkaus)))
       {:luokka "napiton-nappi" :paksu? true}]])

   [:hr]

   [:div.col-xs-12
    [lukutila-rivi "Työmenetelmä" (paikkaus/tyomenetelma-id->nimi (:tyomenetelma lomake) tyomenetelmat)]
    ;; Sijainti
    [lukutila-rivi "Sijainti" (tr-domain/tr-osoite-moderni-fmt (:tie lomake)
                                                               (:aosa lomake) (:aet lomake)
                                                               (:losa lomake) (:let lomake))]
    ;; Pituus
    [lukutila-rivi "Kohteen pituus" (str (:pituus lomake) " m")]
    ;; Aikataulu
    [lukutila-rivi
     "Suunniteltu aikataulu"
     (if (and (:alkupvm lomake) (:loppupvm lomake))
       (harja.fmt/pvm-vali [(:alkupvm lomake) (:loppupvm lomake)])
       "Suunniteltua aikataulua ei löytynyt")]
    [lukutila-rivi "Suunniteltu määrä" (str (:suunniteltu-maara lomake) " " (:yksikko lomake))] ;; :koostettu-maara
    (when (oikeudet/urakat-paikkaukset-paikkauskohteetkustannukset (-> @tila/tila :yleiset :urakka :id))
      [lukutila-rivi "Suunniteltu hinta" (fmt/euro-opt (:suunniteltu-hinta lomake))])
    [lukutila-rivi "Lisätiedot" (:lisatiedot lomake)]
    [:div {:style {:padding-bottom "16px"}}]]])

(defn raporointi-header [e! lomake muokkaustila? tyomenetelmat]
  [:div.lomake.ei-borderia.lukutila
   [lomake-otsikko lomake]

   [:div.col-xs-12.margin-top-16
    [:span.flex-ja-baseline.margin-top-4
     [:div.lomake-arvo.margin-right-64 (paikkaus/tyomenetelma-id->nimi (:tyomenetelma lomake) tyomenetelmat)]
     [napit/muokkaa "Muokkaa" #(e! (harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake/->AvaaPMRLomake (assoc lomake :tyyppi :paikkauskohteen-muokkaus))) {:luokka "napiton-nappi" :paksu? true}]]
    [:div.lomake-arvo.margin-top-4 (or (:lisatiedot lomake) "")]
    [:div.lomake-arvo.margin-top-4 (tr-domain/tr-osoite-moderni-fmt (:tie lomake)
                                                                    (:aosa lomake) (:aet lomake)
                                                                    (:losa lomake) (:let lomake))]

    [:hr]

    [:span.flex-ja-baseline
     [:h3.margin-right-32 "Raportointi"]
     (when-not muokkaustila?
       #_[napit/muokkaa "Muokkaa" #(e! (t-paikkauskohteet/->AvaaLomake (assoc lomake :tyyppi :paikkauskohteen-katselu)))
          {:luokka "napiton-nappi" :paksu? true}] ;; Tällä hetkellä otettiin pois käytöstä. Katsellaan miten vaikuttaa käytettävyyteen
       [napit/muokkaa "Muokkaa" #(e! (t-paikkauskohteet/->AvaaLomake (assoc lomake :tyyppi :paikkauskohteen-muokkaus)))
        {:luokka "napiton-nappi" :paksu? true}])]]])

(defn- footer-oikeat-napit [e! lomake muokkaustila? raportointitila? voi-tilata? voi-perua? muokattu?]
  [:div {:style {:text-align "end"}}
   ;; Lomake on auki
   (when muokkaustila?
     [napit/yleinen-toissijainen
      "Peruuta"
      #(if muokattu? (varmista-kayttajalta/varmista-kayttajalta
                       {:otsikko "Lomakkeelta poistuminen"
                        :sisalto (str "Lomakkeella on tallentamattomia tietoja. Jos poistut, menetät tekemäsi muutokset. Haluatko varmasti poistua lomakkeelta?")
                        :hyvaksy "Poistu tallentamatta"
                        :peruuta-txt "Palaa lomakkeelle"
                        :toiminto-fn (fn []
                                       (e! (t-paikkauskohteet/->SuljeLomake)))})
                     (e! (t-paikkauskohteet/->SuljeLomake)))
      {:paksu? true}])

   ;; Lukutila, tilaajan näkymä
   (when (and voi-tilata? (not muokkaustila?))
     [napit/yleinen-toissijainen
      "Sulje"
      #(e! (t-paikkauskohteet/->SuljeLomake))
      {:paksu? true}])

   ;; Lukutila, urakoitsijan näkymä
   (when (and (not muokkaustila?) (not voi-tilata?) voi-perua?)
     [napit/yleinen-toissijainen
      "Sulje"
      #(e! (t-paikkauskohteet/->SuljeLomake))
      {:paksu? true}])
   ;; Lukutila - ei voi tilata, eikä voi perua
   (when (and (not muokkaustila?) (not voi-tilata?) (not voi-perua?))
     [napit/yleinen-toissijainen
      "Sulje"
      #(e! (t-paikkauskohteet/->SuljeLomake))
      {:paksu? true}])])

(defn- footer-vasemmat-napit [e! lomake muokkaustila? raportointitila? voi-tilata? voi-perua?]
  (let [voi-tallentaa? (::tila/validi? lomake)]
    [:div

     ;; Raportointitila - muokkaus auki
     (when (and raportointitila? (= :paikkauskohteen-muokkaus (:tyyppi lomake)))
       [:div
        (cond
          ;; Raportointitilassa paikkauskohteen tallennus, kun paikkauskohdetta ei merkitä vielä valmiiksi.
          (not (:paikkaustyo-valmis? lomake))
          [napit/tallenna
           "Tallenna"
           #(e! (t-paikkauskohteet/->TallennaPaikkauskohdeRaportointitilassa (lomake/ilman-lomaketietoja lomake)))
           {:disabled (not voi-tallentaa?) :paksu? true}]

          ;; Raportointitilassa paikkauskohteen tallennus ja valmiiksi merkitseminen, kun tila on "tilattu" ja tiemerkintää ei ole tuhoutunut.
          ;; Tallennuksen yhteydessä avataan modal jossa varmistetaan, että käyttäjä on merkitsemässä tilauksen valmiiksi
          (and (= "tilattu" (:paikkauskohteen-tila lomake))
               (or (not (:tiemerkintaa-tuhoutunut? lomake)) (nil? (:tiemerkintaa-tuhoutunut? lomake))))
          [napit/tallenna
           "Tallenna"
           (t-paikkauskohteet/nayta-modal
             (str "Merkitääntkö kohde \"" (:nimi lomake) "\" valmiiksi?")
             "Tilaaja saa sähköpostiin ilmoituksen kohteen valmistumisesta."
             [napit/palvelinkutsu-nappi
              "Merkitse valmiiksi"
              (fn []
                (let [merkitty-valmiiksi? (:paikkaustyo-valmis? lomake)
                      valmistumispvm (:valiaika-valmistumispvm lomake)
                      takuuaika (:valiaika-takuuaika lomake)
                      tiemerkinta-tuhoutunut? (:tiemerkintaa-tuhoutunut? lomake)]
                  (t-paikkauskohteet/tallenna-tilamuutos! (cond-> lomake
                                                                  true (lomake/ilman-lomaketietoja)
                                                                  merkitty-valmiiksi? (assoc :paikkauskohteen-tila "valmis")
                                                                  valmistumispvm (assoc :valmistumispvm valmistumispvm)
                                                                  takuuaika (assoc :takuuaika takuuaika)
                                                                  tiemerkinta-tuhoutunut? (assoc :tiemerkintaa-tuhoutunut? tiemerkinta-tuhoutunut?)))))
              {:paksu? true
               :ikoni (ikonit/check)
               :kun-onnistuu (fn [vastaus] (e! (t-paikkauskohteet/->MerkitsePaikkauskohdeValmiiksiOnnistui vastaus)))
               :kun-virhe (fn [vastaus] (e! (t-paikkauskohteet/->MerkitsePaikkauskohdeValmiiksiEpaonnistui vastaus)))}]
             [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])
           {:disabled (not voi-tallentaa?)}]

          ;; Raportointitilassa, Kohteen valmiiksi saattaminen, kun tiemerkintää ON tuhoutunut, avaan erillinen modal, jossa
          ;; kirjoitetaan tiemerkintään viesti.
          (and (= "tilattu" (:paikkauskohteen-tila lomake))
               (:tiemerkintaa-tuhoutunut? lomake))
          [napit/tallenna
           "Tallenna"
           #(e! (t-paikkauskohteet/->AvaaTiemerkintaModal (assoc lomake :kopio-itselle? true)))
           {:disabled (not voi-tallentaa?) :paksu? true}]

          ;; Raportointitilassa valmiin kohteen tallentaminen, kun tajutaan jälkikäteen, että tiemerkintää ON tuhoutunut, avaan erillinen modal, jossa
          ;; kirjoitetaan tiemerkintään viesti, mutta valmistuminen on siis tapahtunut jo aiemmin
          (and (= "valmis" (:paikkauskohteen-tila lomake))
               (:tiemerkintaa-tuhoutunut? lomake)
               (nil? (:tiemerkintapvm lomake)))
          [napit/tallenna
           "Tallenna"
           #(e! (t-paikkauskohteet/->AvaaTiemerkintaModal (assoc lomake :kopio-itselle? true)))
           {:disabled (not voi-tallentaa?) :paksu? true}]

          ;; Raportointitilassa valmiin kohteen tallentaminen uusilla tiedoilla
          (= "valmis" (:paikkauskohteen-tila lomake))
          [napit/tallenna
           "Tallenna"
           #(e! (t-paikkauskohteet/->TallennaPaikkauskohdeRaportointitilassa (lomake/ilman-lomaketietoja lomake)))
           {:disabled (not voi-tallentaa?) :paksu? true}])])

     ;; Muokkaustila - Paikkauskohteen tallennus
     (when (and muokkaustila? (not raportointitila?))
       [:div [napit/tallenna
              ;; Kun lisätään uutta, niin käytetään vian "tallenna" sanaa
              (if (:id lomake)
                "Tallenna muutokset"
                "Tallenna")
              #(e! (t-paikkauskohteet/->TallennaPaikkauskohde (lomake/ilman-lomaketietoja lomake)))
              {:disabled (not voi-tallentaa?) :paksu? true}]

        ;; Paikkauskohde on pakko olla tietokannassa, ennenkuin sen voi poistaa
        ;; Ja sen täytyy olla ehdotettu tai hylatty tilassa. Tilattua tai valmista ei voida poistaa
        (when (and (:id lomake)
                   (or (= (:paikkauskohteen-tila lomake) "ehdotettu")
                       (= (:paikkauskohteen-tila lomake) "hylatty")))
          [napit/yleinen-toissijainen
           "Poista kohde"
           (t-paikkauskohteet/nayta-modal
             (str "Poistetaanko kohde \"" (:nimi lomake) "\"?")
             "Toimintoa ei voi perua."
             [napit/yleinen-toissijainen "Poista kohde" #(e! (t-paikkauskohteet/->PoistaPaikkauskohde
                                                               (lomake/ilman-lomaketietoja lomake))) {:paksu? true}]
             [napit/yleinen-toissijainen "Säilytä kohde" modal/piilota! {:paksu? true}])
           {:ikoni (ikonit/livicon-trash) :paksu? true}])])

     ;; Lukutila, tilaajan näkymä
     (when (and voi-tilata? (not muokkaustila?) (not raportointitila?))
       [:div
        [napit/tallenna
         "Tilaa"
         (t-paikkauskohteet/nayta-modal
           (str "Tilataanko kohde \"" (:nimi lomake) "\"?")
           "Urakoitsija saa sähköpostiin ilmoituksen kohteen tilauksesta."
           [napit/palvelinkutsu-nappi
            "Tilaa kohde"
            #(t-paikkauskohteet/tallenna-tilamuutos! (as-> lomake lomake
                                                           (lomake/ilman-lomaketietoja lomake)
                                                           (assoc lomake :paikkauskohteen-tila "tilattu")
                                                           (if (:toteumatyyppi lomake)
                                                             (let [toteumatyyppi (:toteumatyyppi lomake)]
                                                               (-> lomake
                                                                   (assoc :pot? (cond
                                                                                  (= :pot toteumatyyppi) true
                                                                                  (= :normaali toteumatyyppi) false
                                                                                  :else false))
                                                                   (dissoc :toteumatyyppi)))
                                                             lomake)))
            {:paksu? true
             :ikoni (ikonit/check)
             :kun-onnistuu (fn [vastaus] (e! (t-paikkauskohteet/->TilaaPaikkauskohdeOnnistui vastaus)))
             :kun-virhe (fn [vastaus] (e! (t-paikkauskohteet/->TilaaPaikkauskohdeEpaonnistui vastaus)))}]
           [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])
         {:paksu? true
          :disabled (nil? (:ulkoinen-id lomake))}]
        [napit/yleinen-toissijainen
         "Hylkää"
         (t-paikkauskohteet/nayta-modal
           (str "Hylätäänkö kohde " (:nimi lomake) "?")
           "Urakoitsija saa sähköpostiin ilmoituksen kohteen hylkäyksestä."
           [napit/palvelinkutsu-nappi
            "Hylkää kohde"
            #(t-paikkauskohteet/tallenna-tilamuutos! (assoc (lomake/ilman-lomaketietoja lomake) :paikkauskohteen-tila "hylatty"))
            {:paksu? true
             :ikoni (ikonit/check)
             :kun-onnistuu (fn [vastaus] (e! (t-paikkauskohteet/->PeruPaikkauskohteenHylkaysOnnistui vastaus)))
             :kun-virhe (fn [vastaus] (e! (t-paikkauskohteet/->PeruPaikkauskohteenHylkaysEpaonnistui vastaus)))}]
           [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])
         {:paksu? true}]])

     ;; Lukutila, tilaja voi perua tilauksen tai hylätä peruutuksen
     (when (and (not muokkaustila?) (not voi-tilata?) voi-perua?)
       (if (= (:paikkauskohteen-tila lomake) "tilattu")
         [napit/nappi
          "Peru tilaus"
          (t-paikkauskohteet/nayta-modal
            (str "Perutaanko kohteen \"" (:nimi lomake) "\" tilaus ?")
            "Urakoitsija saa sähköpostiin ilmoituksen kohteen perumisesta."
            [napit/palvelinkutsu-nappi
             "Peru tilaus"
             #(t-paikkauskohteet/tallenna-tilamuutos! (assoc (lomake/ilman-lomaketietoja lomake) :paikkauskohteen-tila "ehdotettu"))
             {:paksu? true
              :ikoni (ikonit/check)
              :kun-onnistuu (fn [vastaus] (e! (t-paikkauskohteet/->PeruPaikkauskohteenTilausOnnistui vastaus)))
              :kun-virhe (fn [vastaus] (e! (t-paikkauskohteet/->PeruPaikkauskohteenTilausEpaonnistui vastaus)))}]
            [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])
          {:luokka "napiton-nappi punainen"
           :ikoni (ikonit/livicon-back-circle)}]
         [napit/nappi
          "Kumoa hylkäys"
          (t-paikkauskohteet/nayta-modal
            (str "Perutaanko kohteen " (:nimi lomake) " hylkäys ?")
            "Kohde palautetaan hylätty-tilasta takaisin ehdotettu-tilaan. Urakoitsija saa sähköpostiin ilmoituksen kohteen tilan muutoksesta"
            [napit/palvelinkutsu-nappi
             "Peru hylkäys"
             #(t-paikkauskohteet/tallenna-tilamuutos! (assoc (lomake/ilman-lomaketietoja lomake) :paikkauskohteen-tila "ehdotettu"))
             {:paksu? true
              :ikoni (ikonit/check)
              :kun-onnistuu (fn [vastaus] (e! (t-paikkauskohteet/->PeruPaikkauskohteenHylkaysOnnistui vastaus)))
              :kun-virhe (fn [vastaus] (e! (t-paikkauskohteet/->PeruPaikkauskohteenHylkaysEpaonnistui vastaus)))}]
            [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])
          {:luokka "napiton-nappi punainen"
           :ikoni (ikonit/livicon-back-circle)}]))]))

(defn paikkauskohde-lomake [e! {:keys [lomake toteumalomake pmr-lomake] :as app}]
  (let [muokkaustila? (or
                        (= :paikkauskohteen-muokkaus (:tyyppi lomake))
                        (= :uusi-paikkauskohde (:tyyppi lomake)))
        toteumalomake-auki? (or
                              (= :toteuman-muokkaus (:tyyppi toteumalomake))
                              (= :uusi-toteuma (:tyyppi toteumalomake)))
        pmr-lomake-auki? (= :paikkauskohteen-muokkaus (:tyyppi pmr-lomake))
        toteumatyyppi-arvo (atom (:toteumatyyppi lomake))
        kayttajaroolit (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id))
        tilaaja? (roolit/kayttaja-on-laajasti-ottaen-tilaaja? kayttajaroolit @istunto/kayttaja)
        ;; Paikkauskohde on tilattivissa, kun sen tila on "ehdotettu" ja käyttäjä on tilaaja
        voi-tilata? (or (and
                          (= "ehdotettu" (:paikkauskohteen-tila lomake))
                          tilaaja?)
                        false)
        voi-perua? (and
                     tilaaja?
                     (or
                       (and (= "tilattu" (:paikkauskohteen-tila lomake))
                            (or (nil? (:toteumien-maara lomake)) (= 0 (:toteumien-maara lomake))))
                       (= "hylatty" (:paikkauskohteen-tila lomake))))
        voi-kirjoittaa? (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet
                                                  (-> @tila/tila :yleiset :urakka :id)
                                                  @istunto/kayttaja)
        urakoitsija? (t-paikkauskohteet/kayttaja-on-urakoitsija? kayttajaroolit)
        nayta-muokkaus? (or tilaaja? ;; Tilaaja voi muokata missä tahansa tilassa olevaa paikkauskohdetta
                            ;; Tarkista kirjoitusoikeudet
                            voi-kirjoittaa?
                            ;; Urakoitsija, jolla on periaatteessa kirjoitusoikeudet ei voi muuttaa enää hylättyä kohdetta
                            (and urakoitsija? voi-kirjoittaa?
                                 (not= "hylatty" (:paikkauskohteen-tila lomake)))
                            false) ;; Defaulttina estetään muokkaus
        raportointitila? (or (= "valmis" (:paikkauskohteen-tila lomake))
                             (= "tilattu" (:paikkauskohteen-tila lomake)))
        ;; Pidetään kirjaa validoinnista
        muokattu? (not= (t-paikkauskohteet/lomakkeen-hash lomake) (:alku-hash lomake))
        tyomenetelmat (get-in app [:valinnat :tyomenetelmat])
        ;; Takuuaika määräytyy työmenetelmän perusteella. Mutta tällä hetkellä ei tiedetä, että mikä
        ;; työmenetelmä viittaa mihinkin takuuaikaan, joten asetetaan väliaikaisesti takuuajan defaultiksi 2 vuotta
        lomake (cond

                 (and raportointitila? (not= :pot (:toteumatyyppi lomake)) (nil? (:valmistumispvm lomake)) (nil? (:takuuaika lomake)) (nil? (:valiaika-takuuaika lomake)))
                 (assoc lomake :valiaika-takuuaika 2)
                 (and raportointitila? (= :pot (:toteumatyyppi lomake)) (nil? (:valiaika-takuuaika lomake)))
                 (assoc lomake :valiaika-takuuaika (:takuuaika lomake))
                 :else lomake)]
    [:div.overlay-oikealla {:style {:width "600px" :overflow "auto"}}
     [debug/debug app]
     ;; Näytä tarvittaessa tiemerkintämodal
     (when (:tiemerkintamodal lomake)
       [viesti-tiemerkintaan-modal e! (:tiemerkintalomake app) (:tiemerkintaurakat app) tyomenetelmat])

     ;; Tarkistetaan muokkaustila
     (when (and (not muokkaustila?) (not raportointitila?))
       [lomake-lukutila e! lomake nayta-muokkaus? tyomenetelmat])

     (when toteumalomake-auki?
       [sivupalkki/oikea {:leveys "570px" :jarjestys 2}
        ;; Lisätään yskikkö toteumalomakkeelle, jotta osataan näyttää kenttien otsikkotekstit oikein
        [v-toteumalomake/toteumalomake e!
         (assoc app :toteumalomake (-> toteumalomake
                                       (assoc :toteumien-maara (:toteumien-maara lomake))
                                       (assoc :paikkauskohde-nimi (:nimi lomake))
                                       (assoc :paikkauskohde-tila (:paikkauskohteen-tila lomake))
                                       (assoc :tyomenetelma (:tyomenetelma lomake))
                                       (assoc :kohteen-yksikko (:yksikko lomake))
                                       (assoc :paikkauskohde-id (:id lomake))))]])

     (when pmr-lomake-auki?
       [sivupalkki/oikea {:leveys "570px" :jarjestys 2}
        [v-pmrlomake/pmr-lomake e! pmr-lomake tyomenetelmat]])

     [lomake/lomake
      {:ei-borderia? true
       :voi-muokata? muokkaustila?
       :tarkkaile-ulkopuolisia-muutoksia? true
       :otsikko (when (and muokkaustila? (not raportointitila?))
                  (if (:id lomake) "Muokkaa paikkauskohdetta" "Ehdota paikkauskohdetta"))
       :muokkaa! #(e! (t-paikkauskohteet/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
       :header-fn (when raportointitila? #(raporointi-header e! lomake muokkaustila? tyomenetelmat))
       :footer-fn (fn [lomake]
                    [:div.row
                     [:hr]

                     ;; Tilaajalle näytetään kolmen työmenetelmän kohdalla erillinen pot/toteuma radiobutton valinta.
                     ;; Mikäli tilaaja valitsee pot vaihtoehdon, toteumia ei kirjata normaaliprossin mukaan, vaan pot-lomakkeelta
                     (when (and (not muokkaustila?)
                                (nayta-pot-valinta? lomake tyomenetelmat))
                       [:div.row {:style {:background-color "#F0F0F0" :margin-bottom "24px" :padding-bottom "8px"}}
                        [:div.row
                         [:div.col-xs-12
                          [:h4 "RAPORTOINTITAPA"]]]
                        [:div.row {:style {:padding-left "16px"}}
                         [kentat/tee-kentta {:tyyppi :radio-group
                                             :nimi :toteumatyyppi
                                             :otsikko ""
                                             :vaihtoehdot [:normaali :pot]
                                             :nayta-rivina? true
                                             :vayla-tyyli? true
                                             :vaihtoehto-nayta {:pot "POT-lomake"
                                                                :normaali "Toteumat"}
                                             :valitse-fn #(e! (t-paikkauskohteet/->AsetaToteumatyyppi %))}
                          toteumatyyppi-arvo]]])
                     (when (and (not muokkaustila?)
                                (nil? (:ulkoinen-id lomake)))
                       (kentat/nayta-arvo {:nimi :tiemerkinta-alert
                                           :tyyppi :komponentti
                                           :komponentti (fn []
                                                          [harja.ui.yleiset/varoitus-vihje
                                                           "Tarkista paikkauskohteen tiedot ennen tilausta"
                                                           "Kohteen numero puuttuu"])
                                           ::lomake/col-luokka "col-xs-12"
                                           :rivi-luokka "lomakeryhman-rivi-tausta"}))
                     (when (and (not urakoitsija?) voi-tilata? (not muokkaustila?))
                       [:div.col-xs-9 {:style {:padding "8px 0 8px 0"}} "Urakoitsija saa sähköpostiin ilmoituksen, kuin tilaat tai hylkäät paikkauskohde-ehdotuksen."])

                     ;; UI on jaettu kahteen osioon. Oikeaan ja vasempaan.
                     ;; Tarkistetaan ensin, että mitkä napit tulevat vasemmalle
                     [:div.row
                      [:div.col-xs-8 {:style {:padding-left "0"}}
                       [footer-vasemmat-napit e! lomake muokkaustila? raportointitila? voi-tilata? voi-perua?]]
                      [:div.col-xs-4
                       [footer-oikeat-napit e! lomake muokkaustila? raportointitila? voi-tilata? voi-perua? muokattu?]]]])}
      (paikkauskohde-skeema e! muokkaustila? raportointitila? lomake toteumalomake tyomenetelmat)
      lomake]]))

(defn paikkauslomake [e! app]
  [paikkauskohde-lomake e! app])
