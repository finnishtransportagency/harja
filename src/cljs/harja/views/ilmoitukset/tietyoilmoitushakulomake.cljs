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
            [harja.ui.komponentti :as komp]))

(defn tyotyypit [tyotyypit]
  (str/join ", " (map (fn [t]
                        (str (:tyyppi t)
                             (when (:selite t)
                               (str " (Selite: " (:selite t) ")"))))
                      tyotyypit)))

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
    :hae #(str (or (::t/tr_numero (::t/tr-osoite %)) "(ei tien numeroa)") " " (::t/tien-nimi % "(ei tien nimeä)"))
    :leveys 4}
   {:otsikko "Ilmoitettu" :nimi :ilmoitettu
    :hae (comp pvm/pvm-aika ::t/luotu)
    :leveys 2}
   #_ {:otsikko "Alkupvm" :nimi :alku
    :hae (comp pvm/pvm-aika ::t/alku)
    :leveys 2}
   #_ {:otsikko "Loppupvm" :nimi :loppu
    :hae (comp pvm/pvm-aika ::t/loppu) :leveys 2}
   #_ {:otsikko "Työn tyyppi" :nimi :tyotyypit
    :hae #(s/join ", " (->> % ::t/tyotyypit (map ::t/tietyon-tyyppi)))
    :leveys 4}
   #_{:otsikko "Ilmoittaja" :nimi :ilmoittaja
    :hae #(str (:ilmoittaja_etunimi %) " " (:ilmoittaja_sukunimi %))
    :leveys 7}])

(defn vaikutukset-liikenteelle []
  (lomake/ryhma {:otsikko "Vaikutukset liikenteelle"
                 :uusi-rivi? true}
                {:otsikko "Kaistajärjestelyt"
                 :nimi :kaistajarjestelyt
                 :hae #((keyword (:kaistajarjestelyt %)) t/kaistajarjestelyt)
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
      :hae (comp fmt/lyhennetty-urakan-nimi :urakka_nimi)
      :muokattava? (constantly false)}
     #_ {:otsikko "Urakoitsija"
      :nimi :urakoitsijan_nimi
      :muokattava? (constantly false)}
     #_ {:otsikko "Urakoitsijan yhteyshenkilo"
      :nimi :urakoitsijan_yhteyshenkilo
      :hae #(str
              (:urakoitsijayhteyshenkilo_etunimi %) " "
              (:urakoitsijayhteyshenkilo_sukunimi %) ", "
              (:urakoitsijayhteyshenkilo_matkapuhelin %) ", "
              (:urakoitsijayhteyshenkilo_sahkoposti %))
      :muokattava? (constantly false)}
     #_ {:otsikko "Tilaaja"
      :nimi :tilaajan_nimi
      :muokattava? (constantly false)}
     #_ {:otsikko "Tilaajan yhteyshenkilo"
      :nimi :tilaajan_yhteyshenkilo
      :hae #(str
              (:tilaajayhteyshenkilo_etunimi %) " "
              (:tilaajayhteyshenkilo_sukunimi %) ", "
              (:tilaajayhteyshenkilo_matkapuhelin %) ", "
              (:tilaajayhteyshenkilo_sahkoposti %))
      :muokattava? (constantly false)}
     #_ {:otsikko "Tie"
      :nimi :tie
      :hae #(str (:tien_nimi %) ": "
                 (:tr_numero %) " / "
                 (:tr_alkuosa %) " / "
                 (:tr_alkuetaisyys %) " / "
                 (:tr_loppuosa %) " / "
                 (:tr_loppuetaisyys %))
      :muokattava? (constantly false)}
     #_ {:otsikko "Alkusijainti"
      :nimi :alkusijainnin_kuvaus
      :muokattava? (constantly false)}
     #_ {:otsikko "Alku"
      :nimi :alku
      :tyyppi :pvm-aika
      :muokattava? (constantly false)}
     #_ {:otsikko "Loppusijainti"
      :nimi :loppusijainnin_kuvaus
      :muokattava? (constantly false)}
     #_ {:otsikko "Loppu"
      :nimi :loppu
      :tyyppi :pvm-aika
      :muokattava? (constantly false)}
     #_ {:otsikko "Kunnat"
      :nimi :kunnat
      :muokattava? (constantly false)}
     #_ {:otsikko "Työtyypit"
      :nimi :tyotyypit
      :hae #(tyotyypit (:tyotyypit %))
      :muokattava? (constantly false)}
     #_(vaikutukset-liikenteelle)]
    tietyoilmoitus]
   [napit/muokkaa "Muokkaa" #(e! (tiedot/->ValitseIlmoitus tietyoilmoitus)) {}]
   #_ [grid
    {:otsikko "Työvaiheet"
     :tyhja "Ei löytyneitä tietoja"
     :rivi-klikattu (when-not ilmoituksen-haku-kaynnissa? #(e! (tiedot/->ValitseIlmoitus %)))
     :piilota-toiminnot true
     :max-rivimaara 500
     :max-rivimaaran-ylitys-viesti "Yli 500 ilmoitusta. Tarkenna hakuehtoja."}
    (ilmoitustaulukon-kentat)
    (:tyovaiheet tietyoilmoitus)]])

(defn ilmoitukset [e! app haetut-ilmoitukset ilmoituksen-haku-kaynnissa?]
  (log "---> " (pr-str (dissoc (first haetut-ilmoitukset) ::t/osoite)))
  [:div
   [grid
    {:id "tietyoilmoitushakutulokset"
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
            :hae #(count (:tyovaiheet %))
            :leveys 2}])
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