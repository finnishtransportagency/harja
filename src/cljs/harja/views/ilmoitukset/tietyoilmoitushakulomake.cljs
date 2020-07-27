(ns harja.views.ilmoitukset.tietyoilmoitushakulomake
  (:require [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [clojure.string :as s]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [harja.views.ilmoitukset.tietyoilmoitukset-yhteinen :as tietyo-yhteiset]
            [harja.ui.grid :refer [grid]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+
                                      kuvaus-ja-avainarvopareja]]
            [harja.ui.valinnat :as valinnat]
            [reagent.core :as r]
            [harja.domain.tietyoilmoitus :as t]
            [harja.domain.tietyoilmoituksen-email :as e]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.kayttaja :as ka]
            [clojure.string :as str]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.domain.tierekisteri :as tr]
            [harja.transit :as transit]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn vie-pdf
  "Nappi, joka avaa PDF-latauksen uuteen välilehteen."
  [id]
  [:form {:target "_blank"
          :method "POST"
          :action (k/pdf-url :tietyoilmoitus)}
   [:input {:type "hidden" :name "parametrit"
            :value (transit/clj->transit {:id id})}]
   [:button.nappi-ensisijainen.pull-right
    {:type "submit" :on-click #(.stopPropagation %)}
    (ikonit/print) " PDF"]])

(defn ilmoitusten-hakuehdot [e! valinnat-nyt kayttajan-urakat]
  (let [urakkavalinnat (into [{:id nil :nimi "Ei rajausta (haetaan myös urakattomat)"}]
                             kayttajan-urakat)]
    [lomake/lomake
     {:luokka :horizontal
      :muokkaa! #(e! (tiedot/->AsetaValinnat %))}
     [(valinnat/aikavalivalitsin
        "Luotu välillä"
        tiedot/luonti-aikavalit
        valinnat-nyt
        {:vakioaikavali :luotu-vakioaikavali
         :alkuaika :luotu-alkuaika
         :loppuaika :luotu-loppuaika}
        true)
      (valinnat/aikavalivalitsin
        "Käynnissä välillä"
        tiedot/kaynnissa-aikavalit
        valinnat-nyt
        {:vakioaikavali :kaynnissa-vakioaikavali
         :alkuaika :kaynnissa-alkuaika
         :loppuaika :kaynnissa-loppuaika}
        true)
      {:nimi :urakka-id
       :otsikko "Urakka"
       :tyyppi :valinta
       :pakollinen? true
       :valinnat urakkavalinnat
       :valinta-nayta :nimi
       :valinta-arvo :id
       :muokattava? (constantly true)
       :palstoja 1}
      {:nimi :tierekisteriosoite
       :tyyppi :tierekisteriosoite
       :pakollinen? false
       :sijainti (r/wrap (:sijainti valinnat-nyt)
                         #(e! (tiedot/->PaivitaSijainti %)))
       :otsikko "Tierekisteriosoite"
       :palstoja 1
       :tyhjennys-sallittu? true
       :validoi [(fn [osoite]
                   (cond
                     (not (tr/validi-osoite? osoite))
                     "Osoite ei ole validi"

                     (not (tr/on-alku-ja-loppu? osoite))
                     "Vaaditaan sekä alku- että loppuosa"

                     :else
                     nil))]}
      {:nimi :vain-kayttajan-luomat
       :tyyppi :checkbox
       :teksti "Vain minun luomani"
       :palstoja 1}]
     valinnat-nyt]))

(defn ilmoitustaulukon-kentat []
  [{:tyyppi :vetolaatikon-tila :leveys 1}
   {:otsikko "Urakka" :nimi ::t/urakan-nimi :leveys 5}
   {:otsikko "Tie" :nimi :tie
    :hae #(str (or (::tr/tie (::t/osoite %)) "(ei tien numeroa)") " " (::t/tien-nimi % "(ei tien nimeä)"))
    :leveys 4}
   {:otsikko "Ilmoitettu" :nimi ::m/luotu
    :fmt pvm/pvm-aika-opt
    :leveys 2}
   {:otsikko "Alkupvm" :nimi ::t/alku
    :fmt pvm/pvm-aika-opt
    :leveys 2}
   {:otsikko "Loppupvm" :nimi ::t/loppu
    :fmt pvm/pvm-aika-opt
    :leveys 2}
   {:otsikko "Työn tyyppi" :nimi ::t/tyotyypit
    :hae t/tyotyypit->str
    :leveys 3}
   {:otsikko "Ilmoittaja" :nimi :ilmoittaja
    :hae t/ilmoittaja->str
    :leveys 5}
   (when (istunto/ominaisuus-kaytossa? :tietyoilmoitusten-lahetys)
     {:otsikko "Vii\u00ADmeisin sähkö\u00ADposti" :tyyppi :komponentti :leveys 2 :hae identity
      :komponentti (fn [rivi _]
                     [tietyo-yhteiset/kuittauksen-tila (when-let [email-lahetykset (::t/email-lahetykset rivi)]
                                                         (last (sort-by ::e/lahetetty #(pvm/ennen? %1 %2) email-lahetykset)))])
      :muokattava? (constantly false)})
   {:otsikko " "
    :leveys 2
    :nimi :vie-pdf
    :tyyppi :komponentti
    :komponentti (fn [{id ::t/id}]
                   [vie-pdf id])}])

(defn vaikutukset-liikenteelle []
  (lomake/ryhma {:otsikko "Vaikutukset liikenteelle"
                 :uusi-rivi? true}
                {:otsikko "Kaistajärjestelyt"
                 :nimi :kaistajarjestelyt
                 :hae (comp t/kaistajarjestelyt ::t/jarjestely ::t/kaistajarjestelyt)
                 :muokattava? (constantly false)}
                {:otsikko "Nopeusrajoitukset"
                 :nimi :nopeusrajoitukset
                 :muokattava? (constantly false)
                 :hae t/nopeusrajoitukset->str}
                {:otsikko "Tienpinta työmaalla"
                 :nimi :tienpinnat
                 :muokattava? (constantly false)
                 :hae t/tienpinnat->str}
                {:otsikko (str "Kiertotie")
                 :nimi :kiertotie
                 :muokattava? (constantly false)
                 :hae t/kiertotienpinnat->str}))

(defn tietyoilmoituksen-vetolaatikko [e!
                                      {ilmoituksen-haku-kaynnissa? :ilmoituksen-haku-kaynnissa? :as app}
                                      tietyoilmoitus]
  [:div
   [lomake/lomake
    {:otsikko "Tiedot koko kohteesta"
     :ei-borderia? true}
    [{:otsikko "Urakka"
      :nimi :urakka_nimi
      :palstoja 2
      :hae (comp fmt/lyhennetty-urakan-nimi ::t/urakan-nimi)
      :muokattava? (constantly false)}
     {:otsikko "Urakoitsija"
      :nimi ::t/urakoitsijan-nimi
      :muokattava? (constantly false)}
     {:otsikko "Urakoitsijan y-tunnus"
      :nimi ::t/urakoitsijan-ytunnus
      :muokattava? (constantly false)}
     {:otsikko "Urakoitsijan yhteyshenkilo"
      :nimi :urakoitsijan_yhteyshenkilo
      :hae t/urakoitsijayhteyshenkilo->str
      :muokattava? (constantly false)}
     {:otsikko "Tilaaja"
      :nimi ::t/tilaajan-nimi
      :muokattava? (constantly false)}
     {:otsikko "Tilaajan yhteyshenkilo"
      :nimi :tilaajan_yhteyshenkilo
      :hae t/tilaajayhteyshenkilo->str
      :muokattava? (constantly false)}
     {:otsikko "Tie"
      :nimi ::t/osoite
      :fmt tr/tierekisteriosoite-tekstina
      :muokattava? (constantly false)}
     {:otsikko "Alkusijainti"
      :nimi ::t/alkusijainnin-kuvaus
      :muokattava? (constantly false)}
     {:otsikko "Alku"
      :nimi ::t/alku
      :tyyppi :pvm-aika
      :muokattava? (constantly false)}
     {:otsikko "Loppusijainti"
      :nimi ::t/loppusijainnin-kuvaus
      :muokattava? (constantly false)}
     {:otsikko "Loppu"
      :nimi ::t/loppu
      :tyyppi :pvm-aika
      :muokattava? (constantly false)}
     {:otsikko "Kunnat"
      :nimi ::t/kunnat
      :muokattava? (constantly false)}
     {:otsikko "Työtyypit"
      :nimi ::t/tyotyypit
      :hae t/tyotyypit->str
      :muokattava? (constantly false)}
     (vaikutukset-liikenteelle)
     (when (istunto/ominaisuus-kaytossa? :tietyoilmoitusten-lahetys)
       {:nimi :email-lahetykset-tloikiin
        :otsikko "PDF:n sähkö\u00ADposti\u00ADlähetykset Harjasta Tie\u00ADliikenne\u00ADkeskuksen sähkö\u00ADpostiin"
        :tyyppi :komponentti
        :komponentti (fn [{:keys [data]}]
                       [tietyo-yhteiset/tietyoilmoituksen-lahetystiedot-komponentti data])})]
    tietyoilmoitus]
   [napit/muokkaa "Muokkaa" #(e! (tiedot/->ValitseIlmoitus tietyoilmoitus)) {}]
   [napit/uusi "Lisää työvaihe" #(e! (tiedot/->AloitaUusiTyovaiheilmoitus tietyoilmoitus)) {}]
   [grid
    {:otsikko "Työvaiheet"
     :tunniste ::t/id
     :tyhja "Ei työvaiheita"
     :rivi-klikattu (when-not ilmoituksen-haku-kaynnissa? #(e! (tiedot/->ValitseIlmoitus %)))
     :piilota-toiminnot true
     :max-rivimaara 500
     :max-rivimaaran-ylitys-viesti "Yli 500 ilmoitusta. Tarkenna hakuehtoja."}
    (ilmoitustaulukon-kentat)
    (::t/tyovaiheet tietyoilmoitus)]])

(defn ilmoitukset [e! app haetut-ilmoitukset ilmoituksen-haku-kaynnissa?]
  [:div
   [grid
    {:id "tietyoilmoitushakutulokset"
     :tunniste ::t/id
     :tyhja (if haetut-ilmoitukset
              "Ei löytyneitä tietoja"
              [ajax-loader "Haetaan ilmoituksia"])
     :rivi-klikattu (when-not ilmoituksen-haku-kaynnissa? #(e! (tiedot/->ValitseIlmoitus %)))
     :piilota-toiminnot true
     :max-rivimaara 500
     :max-rivimaaran-ylitys-viesti "Yli 500 ilmoitusta. Tarkenna hakuehtoja."
     :vetolaatikot (into {}
                         (map (juxt ::t/id (fn [rivi] [tietyoilmoituksen-vetolaatikko e! app rivi])))
                         haetut-ilmoitukset)}
    (into (ilmoitustaulukon-kentat)
          [{:otsikko "Vai\u00ADhei\u00ADta"
            :nimi :vaiheita
            :hae #(count (::t/tyovaiheet %))
            :leveys 1}])
    haetut-ilmoitukset]])

(defn hakulomake
  [e! _]
  (komp/luo
    (komp/sisaan #(e! (tiedot/->HaeIlmoitukset)))
    (fn [e! {valinnat-nyt :valinnat
             haetut-ilmoitukset :tietyoilmoitukset
             ilmoituksen-haku-kaynnissa? :ilmoituksen-haku-kaynnissa?
             kayttajan-urakat :kayttajan-urakat
             :as app}]
      [:span.tietyoilmoitukset
       [napit/uusi "Lisää tietyöilmoitus" #(e! (tiedot/->AloitaUusiTietyoilmoitus @nav/valittu-urakka-id))
        {:disabled (:aloitetaan-uusi-tietyoilmoitus? app)
         :tallennus-kaynnissa? (:aloitetaan-uusi-tietyoilmoitus? app)}]
       [ilmoitusten-hakuehdot e! valinnat-nyt kayttajan-urakat]
       [ilmoitukset e! app haetut-ilmoitukset ilmoituksen-haku-kaynnissa?]])))
