(ns harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohdelomake
  (:require [harja.loki :refer [log]]
            [clojure.string :as str]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.roolit :as roolit]
            [harja.domain.paikkaus :as paikkaus]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.lomake :as lomake]
            [harja.ui.modal :as modal]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.viesti :as viesti]
            [harja.ui.sivupalkki :as sivupalkki]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as t-toteumalomake]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-toteumalomake :as v-toteumalomake]
            [harja.views.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake :as v-pmrlomake]))

(defn nayta-virhe? [polku lomake]
  (let [validi? (if (nil? (get-in lomake polku))
                  true ;; kokeillaan palauttaa true, jos se on vaan tyhjä. Eli ei näytetä virhettä tyhjälle kentälle
                  (get-in lomake [::tila/validius polku :validi?]))]
    ;; Koska me pohjimmiltaan tarkistetaan, validiutta, mutta palautetaan tieto, että näytetäänkö virhe, niin käännetään
    ;; boolean ympäri
    (not validi?)))

(defn- viesti-tiemerkintaan-modal [e! lomake tiemerkintaurakat]
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
         :fmt paikkaus/kuvaile-tyomenetelma
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
      :virhe? (nayta-virhe? [:alkupvm] lomake)
      ::lomake/col-luokka "col-sm-6"}
     {:otsikko "Arv. lopetus"
      :tyyppi :pvm
      :nimi :loppupvm
      :pakollinen? true
      :vayla-tyyli? true
      :pvm-tyhjana #(:alkupvm %)
      :rivi lomake
      :virhe? (nayta-virhe? [:loppupvm] lomake)
      ::lomake/col-luokka "col-sm-6"})
   (lomake/rivi
     {:otsikko "Suunniteltu määrä"
      :tyyppi :positiivinen-numero
      :nimi :suunniteltu-maara
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:suunniteltu-maara] lomake)
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
      :virhe? (nayta-virhe? [:yksikko] lomake)
      ::lomake/col-luokka "col-sm-2"
      :disabled? (when (= "tilattu" (:paikkauskohteen-tila lomake))
                   true)}
     {:otsikko "Suunniteltu hinta"
      :tyyppi :numero
      :desimaalien-maara 2
      :piilota-yksikko-otsikossa? true
      :nimi :suunniteltu-hinta
      ::lomake/col-luokka "col-sm-6"
      :pakollinen? true
      :vayla-tyyli? true
      :virhe? (nayta-virhe? [:suunniteltu-hinta] lomake)
      :yksikko "€"
      :disabled? (when (= "tilattu" (:paikkauskohteen-tila lomake))
                   true)})
   (lomake/rivi
     {:otsikko "Lisätiedot"
      :tyyppi :text
      :nimi :lisatiedot
      :pakollinen? false
      ::lomake/col-luokka "col-sm-12"
      :rivi-luokka "lomakeryhman-rivi-tausta"}
     )])

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

(defn- raportoinnin-kentat [e! lomake toteumalomake voi-muokata?]
  (let [urakoitsija? (t-paikkauskohteet/kayttaja-on-urakoitsija? (roolit/urakkaroolit @istunto/kayttaja (-> @tila/tila :yleiset :urakka :id)))
        jvh? (roolit/jvh? @istunto/kayttaja)
        valmis? (= "valmis" (:paikkauskohteen-tila lomake))
        toteutunut-hinta (:toteutunut-hinta lomake)
        suunniteltu-hinta (:suunniteltu-hinta lomake)
        erotus (if toteutunut-hinta
                 (- suunniteltu-hinta toteutunut-hinta)
                 0)]
    [(lomake/ryhma
       {:otsikko "Arvioitu aikataulu"
        :ryhman-luokka "lomakeryhman-otsikko-tausta"}
       (when voi-muokata?
         {:otsikko "Arv. aloitus"
          :tyyppi :pvm
          :nimi :alkupvm
          :pakollinen? true
          :vayla-tyyli? true
          :virhe? (nayta-virhe? [:alkupvm] lomake)
          ::lomake/col-luokka "col-sm-6"})
       (when voi-muokata?
         {:otsikko "Arv. lopetus"
          :tyyppi :pvm
          :nimi :loppupvm
          :pakollinen? true
          :vayla-tyyli? true
          :pvm-tyhjana #(:alkupvm %)
          :rivi lomake
          :virhe? (nayta-virhe? [:loppupvm] lomake)
          ::lomake/col-luokka "col-sm-6"})
       (when (not voi-muokata?)
         {:tyyppi :string
          :piilota-label? true
          :nimi :aikataulu
          :hae #(str (pvm/paiva-kuukausi (:alkupvm %)) "-" (pvm/pvm (:loppupvm %)))}))

     (lomake/ryhma
       (merge {:otsikko "Paikkaustyö"
               :ryhman-luokka "lomakeryhman-otsikko-tausta"}
              ;; Urakoitsijat ja järjestelmävalvojat voivat lisätä toteumia, jos työmenetelmä ei ole UREM
              (when (and (= :normaali (:toteumatyyppi lomake))
                         (not= "UREM" (:tyomenetelma lomake))
                         (or urakoitsija? jvh?))
                {:nappi [napit/yleinen-toissijainen "Lisää toteuma"
                         #(e! (t-toteumalomake/->AvaaToteumaLomake (assoc toteumalomake :tyyppi :uusi-toteuma)))
                         {:paksu? true
                          :ikoni (ikonit/livicon-plus)}]}))

       (lomake/rivi
         {:otsikko "Toteutusaika"
          :tyyppi :string
          :nimi :toteutusaika
          :muokattava? (constantly false)
          :hae #(let [valmis? (= "valmis" (:paikkauskohteen-tila %))
                      aloitusaika (:toteutus-alkuaika %)
                      lopetusaika (:toteutus-loppuaika %)]
                  (cond
                    (and aloitusaika (not valmis?)) (pvm/pvm aloitusaika)
                    (and aloitusaika lopetusaika valmis?) (str (pvm/paiva-kuukausi aloitusaika) " - " (pvm/pvm lopetusaika))
                    :oletus "–"))
          :rivi-luokka "lomakeryhman-rivi-tausta"
          ::lomake/col-luokka "col-sm-4"}
         {:otsikko "Valmistumispäivä"
          :tyyppi :pvm
          :nimi :valmistumispvm ;; Tarkista, kunhan tietomalli päivitetty
          :muokattava? (constantly false)
          :jos-tyhja "–"
          ::lomake/col-luokka "col-sm-4"}
         {:otsikko "Takuuaika"
          :tyyppi :string
          :nimi :takuuaika
          :muokattava? (constantly false)
          :fmt #(if (nil? %) "–" (str % " vuotta"))
          ::lomake/col-luokka "col-sm-4"})

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
          :tyyppi :numero
          :nimi :toteutunut-maara
          :muokattava? (constantly false)
          :jos-tyhja "–"
          ::lomake/col-luokka "col-sm-4"}
         {:otsikko "Kirjatut toteumat"
          :tyyppi :string
          :nimi :toteumien-maara
          :muokattava? (constantly false)})

       (when (and voi-muokata? (not valmis?) (or urakoitsija? jvh?))
         {:teksti "Paikkaustyö on valmis"
          :nimi :paikkaustyo-valmis?
          :tyyppi :checkbox
          :vayla-tyyli? true
          :disabled? (not (<= 1 (:toteumien-maara lomake)))
          ::lomake/col-luokka "col-sm-12"
          :rivi-luokka "lomakeryhman-rivi-tausta"})

       (when (and (not valmis?) (:paikkaustyo-valmis? lomake) (<= 1 (:toteumien-maara lomake)) voi-muokata? (or urakoitsija? jvh?))
         (lomake/rivi
           {:otsikko "Valmistumispvm"
            :tyyppi :pvm
            :nimi :valiaika-valmistumispvm
            :vayla-tyyli? true
            :virhe? (nayta-virhe? [:alkupvm] lomake)
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
            :vayla-tyyli? true}))

       (when (and voi-muokata? (or urakoitsija? jvh?))
         {:teksti "Tiemerkintää tuhoutunut"
          :nimi :tiemerkintaa-tuhoutunut?
          :vayla-tyyli? true
          :tyyppi :checkbox
          :uusi-rivi? true
          :disabled? (not (or valmis? (:paikkaustyo-valmis? lomake)))
          ::lomake/col-luokka "col-sm-12"
          :vihje "Kirjoita viesti tiemerkinnälle tallennuksen yhteydessä"
          :rivi-luokka "lomakeryhman-rivi-tausta"}))

     (lomake/ryhma
       {:otsikko "Kustannukset"
        :ryhman-luokka "lomakeryhman-otsikko-tausta"}
       (lomake/rivi
         {:otsikko "Suunniteltu hinta"
          :tyyppi :string
          :nimi :suunniteltu-hinta
          :muokattava? (constantly false)
          :fmt #(str % " €")
          :rivi-luokka "lomakeryhman-rivi-tausta"
          ::lomake/col-luokka "col-sm-4"}
         {:otsikko "Toteutunut hinta"
          :tyyppi :numero
          :yksikko "€"
          :jos-tyhja "–"
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
                  (str (- (:toteutunut-hinta %) (:suunniteltu-hinta %)) " €")
                  "–")
          ::lomake/col-luokka "col-sm-4"}))]))

(defn paikkauskohde-skeema [e! voi-muokata? raportointitila? lomake toteumalomake]
  (let [nimi-nro-ja-tp (when voi-muokata?
                         (nimi-numero-ja-tp-kentat lomake))
        sijainti (when voi-muokata?
                   (sijainnin-kentat lomake))
        suunnitelma (when voi-muokata?
                      (suunnitelman-kentat lomake))
        raportointi (when raportointitila? (raportoinnin-kentat e! lomake toteumalomake voi-muokata?))]
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
  [lomake]
  (let [
        nayta? (and (t-paikkauskohteet/kayttaja-on-tilaaja? (roolit/osapuoli @istunto/kayttaja))
                    (= "ehdotettu" (:paikkauskohteen-tila lomake))
                    (paikkaus/levittimella-tehty? lomake))]
    nayta?))

(defn- lomake-otsikko [lomake]
  [:<>
   [:div {:style {:padding-left "16px" :padding-top "32px"}}
    [:div.pieni-teksti (:nro lomake)]
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

(defn- lomake-lukutila [e! lomake nayta-muokkaus? toteumalomake toteumalomake-auki?]
  [:div
   [lomake-otsikko lomake]
   ;; Jos kohde on hylätty, urakoitsija ei voi muokata sitä enää.
   (when nayta-muokkaus?
     [:div.col-xs-12 {:style {:padding-top "24px"}}
      [napit/muokkaa "Muokkaa kohdetta" #(e! (t-paikkauskohteet/->AvaaLomake (assoc lomake :tyyppi :paikkauskohteen-muokkaus))) {:luokka "napiton-nappi" :paksu? true}]])

   [:hr]

   [:div.col-xs-12
    [lukutila-rivi "Työmenetelmä" (paikkaus/kuvaile-tyomenetelma (:tyomenetelma lomake))]
    ;; Sijainti
    [lukutila-rivi "Sijainti" (t-paikkauskohteet/fmt-sijainti (:tie lomake) (:aosa lomake) (:losa lomake)
                                                              (:aet lomake) (:let lomake))]
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

(defn raporointi-header [e! lomake muokkaustila?]
  [:div.lomake.ei-borderia.lukutila
   [lomake-otsikko lomake]

   [:div.col-xs-12.margin-top-16
    [:span.flex-ja-baseline.margin-top-4
     [:div.lomake-arvo.margin-right-64 (paikkaus/kuvaile-tyomenetelma (:tyomenetelma lomake))]
     [napit/muokkaa "Muokkaa" #(e! (harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake/->AvaaPMRLomake (assoc lomake :tyyppi :paikkauskohteen-muokkaus))) {:luokka "napiton-nappi" :paksu? true}]]
    [:div.lomake-arvo.margin-top-4 (or (:lisatiedot lomake) "Ei lisätietoja")]
    [:div.lomake-arvo.margin-top-4 (t-paikkauskohteet/fmt-sijainti (:tie lomake) (:aosa lomake) (:losa lomake)
                                                                   (:aet lomake) (:let lomake))]

    [:hr]

    [:span.flex-ja-baseline
     [:h3.margin-right-32 "Raportointi"]
     (if muokkaustila?
       [napit/muokkaa "Muokkaa" #(e! (t-paikkauskohteet/->AvaaLomake (assoc lomake :tyyppi :paikkauskohteen-katselu))) {:luokka "napiton-nappi" :paksu? true}]
       [napit/muokkaa "Muokkaa" #(e! (t-paikkauskohteet/->AvaaLomake (assoc lomake :tyyppi :paikkauskohteen-muokkaus))) {:luokka "napiton-nappi" :paksu? true}])]]])

(defn- footer-oikeat-napit [e! lomake muokkaustila? raportointitila? voi-tilata? voi-perua?]
  [:div {:style {:text-align "end"}}
   ;; Lomake on auki
   (when muokkaustila?
     [napit/yleinen-toissijainen
      "Peruuta"
      #(e! (t-paikkauskohteet/->SuljeLomake))
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
          (and raportointitila?
               (nil? (:valmistumispvm lomake))
               (or (not (:paikkaustyo-valmis? lomake))
                   (nil? (:paikkaustyo-valmis? lomake))))
          [napit/tallenna
           "Tallenna - raportointi not-valmis"
           #(e! (t-paikkauskohteet/->TallennaPaikkauskohdeRaportointitilassa (lomake/ilman-lomaketietoja lomake)))
           {:disabled (not voi-tallentaa?) :paksu? true}]

          ;; Raportointitilassa paikkauskohteen tallennus ja valmiiksi merkitseminen, kun tiemerkintää ei ole tuhoutunut.
          ;; Tallennuksen yhteydessä avataan modal jossa varmistetaan, että käyttäjä on merkitsemässä tilauksen valmiiksi
          (and raportointitila?
               (or (:paikkaustyo-valmis? lomake) (not (nil? (:valmistumispvm lomake))))
               (or (not (:tiemerkintaa-tuhoutunut? lomake)) (nil? (:tiemerkintaa-tuhoutunut? lomake))))
          [napit/tallenna
           "Tallenna - raportointi - valmis - tiemerkintäOK"
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
             [napit/yleinen-toissijainen "Kumoa" modal/piilota! {:paksu? true}])]

          ;; Raportointitilassa, kun tiemerkintää ON tuhoutunut, avaan erillinen modal, jossa
          ;; kirjoitetaan tiemerkintään viesti.
          (and raportointitila?
               (or (:paikkaustyo-valmis? lomake) (not (nil? (:valmistumispvm lomake))))
               (= (:tiemerkintaa-tuhoutunut? lomake)))
          [napit/tallenna
           "Tallenna - raportointi - valmis - tiemerkintäTUHOU"
           #(e! (t-paikkauskohteet/->AvaaTiemerkintaModal (assoc lomake :kopio-itselle? true)))
           {:disabled (not voi-tallentaa?) :paksu? true}])])

     ;; Muokkaustila - Paikkauskohteen tallennus
     (when (and muokkaustila? (not raportointitila?))
       [:div [napit/tallenna
              "Tallenna muutokset"
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
         {:paksu? true}]
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

     ;; Lukutila, tiljaa voi perua tilauksen tai hylätä peruutuksen
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
        kayttajarooli (roolit/osapuoli @istunto/kayttaja)
        ;; Paikkauskohde on tilattivissa, kun sen tila on "ehdotettu" ja käyttäjä on tilaaja
        voi-tilata? (or (and
                          (= "ehdotettu" (:paikkauskohteen-tila lomake))
                          (= :tilaaja kayttajarooli))
                        false)
        voi-perua? (and
                     (= :tilaaja kayttajarooli)
                     (or
                       (= "tilattu" (:paikkauskohteen-tila lomake))
                       (= "hylatty" (:paikkauskohteen-tila lomake))))
        nayta-muokkaus? (or (= :tilaaja (roolit/osapuoli @istunto/kayttaja)) ;; Tilaaja voi muokata missä tahansa tilassa olevaa paikkauskohdetta
                            ;; Tarkista kirjoitusoikeudet
                            (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet
                                                      (-> @tila/tila :yleiset :urakka :id)
                                                      @istunto/kayttaja)
                            ;; Urakoitsija, jolla on periaatteessa kirjoitusoikeudet ei voi muuttaa enää hylättyä kohdetta
                            (and (= :urakoitsija (roolit/osapuoli @istunto/kayttaja))
                                 (oikeudet/voi-kirjoittaa? oikeudet/urakat-paikkaukset-paikkauskohteet
                                                           (-> @tila/tila :yleiset :urakka :id)
                                                           @istunto/kayttaja)
                                 (not= "hylatty" (:paikkauskohteen-tila lomake)))

                            false ;; Defaulttina estetään muokkaus
                            )
        raportointitila? (or (= "valmis" (:paikkauskohteen-tila lomake))
                             (= "tilattu" (:paikkauskohteen-tila lomake)))
        ;; Pidetään kirjaa validoinnista
        voi-tallentaa? (::tila/validi? lomake)]
    [:div.overlay-oikealla {:style {:width "600px" :overflow "auto"}}
     ;; Näytä tarvittaessa tiemerkintämodal
     (when (:tiemerkintamodal lomake)
       [viesti-tiemerkintaan-modal e! (:tiemerkintalomake app) (:tiemerkintaurakat app)])

     ;; Tarkistetaan muokkaustila
     (when (and (not muokkaustila?) (not raportointitila?))
       [lomake-lukutila e! lomake nayta-muokkaus? toteumalomake toteumalomake-auki?])

     (when toteumalomake-auki?
       [sivupalkki/oikea {:leveys "570px" :jarjestys 2}
        ;; Liäsään yskikkö toteumalomakkeelle, jotta osataan näyttää kenttien otsikkotekstit oikein
        [v-toteumalomake/toteumalomake e!
         (-> toteumalomake
             (assoc :toteumien-maara (:toteumien-maara lomake))
             (assoc :paikkauskohde-nimi (:nimi lomake))
             (assoc :tyomenetelma (:tyomenetelma lomake))
             (assoc :kohteen-yksikko (:yksikko lomake))
             (assoc :paikkauskohde-id (:id lomake)))]])

     (when pmr-lomake-auki?
       [sivupalkki/oikea {:leveys "570px" :jarjestys 2}
        [v-pmrlomake/pmr-lomake e! pmr-lomake]])

     [lomake/lomake
      {:ei-borderia? true
       :voi-muokata? muokkaustila?
       :tarkkaile-ulkopuolisia-muutoksia? true
       :otsikko (when (and muokkaustila? (not raportointitila?)
                           (if (:id lomake) "Muokkaa paikkauskohdetta" "Ehdota paikkauskohdetta")))
       :muokkaa! #(e! (t-paikkauskohteet/->PaivitaLomake (lomake/ilman-lomaketietoja %)))
       :header-fn (when raportointitila? #(raporointi-header e! lomake muokkaustila?))
       :footer-fn (fn [lomake]
                    (let [urakoitsija? (= (roolit/osapuoli @istunto/kayttaja) :urakoitsija)]
                      [:div.row
                       [:hr]

                       ;; Tilaajalle näytetään kolmen työmenetelmän kohdalla erillinen pot/toteuma radiobutton valinta.
                       ;; Mikäli tilaaja valitsee pot vaihtoehdon, toteumia ei kirjata normaaliprossin mukaan, vaan pot-lomakkeelta
                       (when (and (not muokkaustila?)
                                  (nayta-pot-valinta? lomake))
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

                       (when (and (not urakoitsija?) voi-tilata? (not muokkaustila?))
                         [:div.col-xs-9 {:style {:padding "8px 0 8px 0"}} "Urakoitsija saa sähköpostiin ilmoituksen, kuin tilaat tai hylkäät paikkauskohde-ehdotuksen."])

                       ;; UI on jaettu kahteen osioon. Oikeaan ja vasempaan.
                       ;; Tarkistetaan ensin, että mitkä näapit tulevat vasemmalle
                       [:div.row
                        [:div.col-xs-8 {:style {:padding-left "0"}}
                         [footer-vasemmat-napit e! lomake muokkaustila? raportointitila? voi-tilata? voi-perua?]]
                        [:div.col-xs-4
                         [footer-oikeat-napit e! lomake muokkaustila? raportointitila? voi-tilata? voi-perua?]]]]))}
      (paikkauskohde-skeema e! muokkaustila? raportointitila? lomake toteumalomake)
      lomake]]))

(defn paikkauslomake [e! app]
  [paikkauskohde-lomake e! app])
