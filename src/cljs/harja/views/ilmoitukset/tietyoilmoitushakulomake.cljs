(ns harja.views.ilmoitukset.tietyoilmoitushakulomake
  (:require [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [clojure.string :as s]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tiedot]
            [harja.ui.grid :refer [grid]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.loki :refer [tarkkaile! log]]
            [cljs.pprint :refer [pprint]]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+
                                      kuvaus-ja-avainarvopareja]]
            [harja.ui.valinnat :as valinnat]
            [reagent.core :as r]
            [harja.domain.tietyoilmoitukset :as t]
            [clojure.string :as str]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.domain.tierekisteri :as tr]
            [harja.transit :as transit]
            [harja.asiakas.kommunikaatio :as k]))

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

(defn tyotyypit [tyypit]
  (str/join ", " (map (fn [t]
                        (str (::t/tyyppi t)
                             (when-let [kuvaus (::t/kuvaus t)]
                               (str " (Selite: " kuvaus ")"))))
                      tyypit)))

(defn nopeusrajoitukset [nopeusrajoitukset]
  (str/join ", " (map (fn [n]
                        (str (:nopeusrajoitus n) " km/h "
                             (when (:matka n)
                               (str " (" (:matka n) " metriä)"))))
                      nopeusrajoitukset)))

(defn tienpinnat [tienpinnat]
  (str/join ", " (map (fn [n]
                        (str (:materiaali n)
                             (when (:matka n)
                               (str " (" (:matka n) " metriä)"))))
                      tienpinnat)))

(defn henkilo [henkilo]
  (str (::t/etunimi henkilo) " " (::t/sukunimi henkilo)))

(defn yhteyshenkilo [{::t/keys [etunimi sukunimi matkapuhelin sahkoposti]}]
  (str
    etunimi " "
    sukunimi ", "
    matkapuhelin ", "
    sahkoposti))

(defn ilmoitusten-hakuehdot [e! valinnat-nyt kayttajan-urakat]
  (let [urakkavalinnat (into [[nil "Kaikki urakat"]]
                             (map (juxt (comp str :id) :nimi))
                             kayttajan-urakat)]
    [lomake/lomake
     {:luokka :horizontal
      :muokkaa! #(e! (tiedot/->AsetaValinnat %))}
     [(valinnat/aikavalivalitsin "Luotu välillä" tiedot/aikavalit valinnat-nyt)
      {:nimi :urakka
       :otsikko "Urakka"
       :tyyppi :valinta
       :pakollinen? true
       :valinnat urakkavalinnat
       :valinta-nayta second
       :valinta-arvo first
       :muokattava? (constantly true)
       :palstoja 1}
      {:nimi :tierekisteriosoite
       :tyyppi :tierekisteriosoite
       :pakollinen? false
       :sijainti (r/wrap (:sijainti valinnat-nyt) #(e! (tiedot/->PaivitaSijainti %)))
       :otsikko "Tierekisteriosoite"
       :palstoja 1
       :tyhjennys-sallittu? true}
      {:nimi :vain-kayttajan-luomat
       :tyyppi :checkbox
       :teksti "Vain minun luomat"
       :palstoja 1}]
     valinnat-nyt]))

(defn ilmoitustaulukon-kentat []
  [{:tyyppi :vetolaatikon-tila :leveys 1}
   {:otsikko "Urakka" :nimi :urakan-nimi :leveys 5
    :hae (comp fmt/lyhennetty-urakan-nimi ::t/urakan-nimi)}
   {:otsikko "Tie" :nimi :tie
    :hae #(str (or (::tr/tie (::t/osoite %)) "(ei tien numeroa)") " " (::t/tien-nimi % "(ei tien nimeä)"))
    :leveys 4}
   {:otsikko "Ilmoitettu" :nimi ::t/luotu
    :fmt pvm/pvm-aika-opt
    :leveys 2}
   {:otsikko "Alkupvm" :nimi ::t/alku
    :fmt pvm/pvm-aika-opt
    :leveys 2}
   {:otsikko "Loppupvm" :nimi ::t/loppu
    :fmt pvm/pvm-aika-opt
    :leveys 2}
   {:otsikko "Työn tyyppi" :nimi ::t/tyotyypit
    :fmt tyotyypit
    :leveys 4}
   {:otsikko "Ilmoittaja" :nimi :ilmoittaja
    :hae (comp henkilo ::t/ilmoittaja)
    :leveys 7}
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
                 :hae #(nopeusrajoitukset (:nopeusrajoitukset %))}
                {:otsikko "Tienpinta työmaalla"
                 :nimi :tienpinnat
                 :muokattava? (constantly false)
                 :hae #(tienpinnat (:tienpinnat %))}
                {:otsikko (str "Kiertotie")
                 :nimi :kiertotie
                 :muokattava? (constantly false)
                 :hae #(tienpinnat (:kiertotienpinnat %))}))

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
     {:otsikko "Urakoitsijan yhteyshenkilo"
      :nimi :urakoitsijan_yhteyshenkilo
      :hae (comp yhteyshenkilo ::t/urakoitsijayhteyshenkilo)
      :muokattava? (constantly false)}
     {:otsikko "Tilaaja"
      :nimi ::t/tilaajan-nimi
      :muokattava? (constantly false)}
     {:otsikko "Tilaajan yhteyshenkilo"
      :nimi :tilaajan_yhteyshenkilo
      :hae (comp yhteyshenkilo ::t/tilaajayhteyshenkilo)
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
      :fmt tyotyypit
      :muokattava? (constantly false)}
     (vaikutukset-liikenteelle)]
    tietyoilmoitus]
   [napit/muokkaa "Muokkaa" #(e! (tiedot/->ValitseIlmoitus tietyoilmoitus)) {}]
   [grid
    {:otsikko "Työvaiheet"
     :tunniste ::t/id
     :tyhja "Ei löytyneitä tietoja"
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
          [{:otsikko "Vaiheita"
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
       [ilmoitusten-hakuehdot e! valinnat-nyt kayttajan-urakat]
       [ilmoitukset e! app haetut-ilmoitukset ilmoituksen-haku-kaynnissa?]])))
