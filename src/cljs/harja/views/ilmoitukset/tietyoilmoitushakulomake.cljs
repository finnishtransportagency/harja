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
            [harja.ui.yleiset :as yleiset :refer [ajax-loader linkki livi-pudotusvalikko +korostuksen-kesto+
                                                  kuvaus-ja-avainarvopareja]]))

(defn aikavalivalitsin [valinnat-nyt]
  (let [alkuaika (:alkuaika valinnat-nyt)
        alkuaikakentta {:nimi :alkuaika
                        :otsikko "Alku"
                        :tyyppi :pvm-aika
                        :validoi [[:ei-tyhja "Anna alkuaika"]]}
        loppuaikakentta {:nimi :loppuaika
                         :otsikko "Loppu"
                         :tyyppi :pvm-aika
                         :validoi [[:ei-tyhja "Anna loppuaika"]
                                   [:pvm-toisen-pvmn-jalkeen alkuaika "Loppuajan on oltava alkuajan jälkeen"]]}]
    (lomake/ryhma
      {:rivi? true}
      alkuaikakentta
      loppuaikakentta)))

(defn ilmoitusten-hakuehdot [e! valinnat-nyt]
  [lomake/lomake
   {:luokka :horizontal
    :muokkaa! #(e! (tiedot/->AsetaValinnat %))}
   [(aikavalivalitsin valinnat-nyt)]
   valinnat-nyt])

(defn hakulomake
  [e! {valinnat-nyt :valinnat
       haetut-ilmoitukset :ilmoitukset
       ilmoituksen-haku-kaynnissa? :ilmoituksen-haku-kaynnissa? :as ilmoitukset}]
  [:span.tietyoilmoitukset

   [ilmoitusten-hakuehdot e! valinnat-nyt]
   [:div
    [grid
     {:tyhja (if haetut-ilmoitukset
               "Ei löytyneitä tietoja"
               [ajax-loader "Haetaan ilmoituksia"])
      :rivi-klikattu (when-not ilmoituksen-haku-kaynnissa? #(e! (tiedot/->ValitseIlmoitus %)))
      :piilota-toiminnot true
      :max-rivimaara 500
      :max-rivimaaran-ylitys-viesti "Yli 500 ilmoitusta. Tarkenna hakuehtoja."}

     [
      {:otsikko "Urakka" :nimi :urakka_nimi :leveys 5
       :hae (comp fmt/lyhennetty-urakan-nimi :urakka_nimi)}
      {:otsikko "Tie" :nimi :tie
       :hae #(str (:tr_numero % "(ei tien numeroa)") " " (:tien_nimi % "(ei tien nimeä)"))
       :leveys 4}
      {:otsikko "Alkupvm" :nimi :alku
       :hae (comp pvm/pvm-aika :alku)

       :leveys 2}
      {:otsikko "Loppupvm" :nimi :loppu
       :hae (comp pvm/pvm-aika :loppu) :leveys 2}
      {:otsikko "Työn tyyppi" :nimi :tyotyypit
       :hae #(s/join ", " (->> % :tyotyypit (map :tyyppi)))
       :leveys 4}
      {:otsikko "Ilmoittaja" :nimi :ilmoittaja
       :hae #(str (:ilmoittaja_etunimi %) " " (:ilmoittaja_sukunimi %))
       :leveys 7}]
     haetut-ilmoitukset]]])