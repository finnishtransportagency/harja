(ns harja.ui.kartta.infopaneelin-sisalto
  "Määrittelee erilaisten kartalle piirrettävien asioiden tiedot, jotka tulevat kartta
  overlay näkymään."
  (:require [clojure.string :as string]
            [harja.pvm :as pvm]
            [harja.loki :as log :refer [log]]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat :as laatupoikkeamat]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.turvallisuuspoikkeamat :as turpodomain]
            [harja.domain.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.domain.tierekisteri :as tierekisteri]))

(defmulti infopaneeli-skeema :tyyppi-kartalla)

(defmethod infopaneeli-skeema :tyokone [tyokone]
  {:tyyppi  :tyokone
   :jarjesta-fn :alkanut
   :otsikko (str "Työkone: "
                 (when (:tehtavat tyokone)
                   (string/join ", " (:tehtavat tyokone)))
                 " " (pvm/pvm-aika (:alkanut tyokone)))
   :tiedot  [{:otsikko "Työ aloitettu" :tyyppi :pvm-aika :nimi :alkanut}
             {:otsikko "Viimeisin havainto" :tyyppi :pvm-aika :nimi :lahetysaika}
             {:otsikko "Tyyppi" :tyyppi :string :nimi :tyokonetyyppi}
             {:otsikko "Organisaatio" :tyyppi :string :hae #(or (:organisaationimi %) "Ei organisaatiotietoja")}
             {:otsikko "Urakka" :tyyppi :string :hae #(or (:urakkanimi %) "Ei urakkatietoja")}
             {:otsikko "Tehtävät" :tyyppi :string
              :hae     #(string/join ", " (:tehtavat %))}]
   :data    tyokone})

(defn ilmoituksen-tiedot [ilmoitus]
  {:tyyppi  :ilmoitus
   :jarjesta-fn :ilmoitettu
   :otsikko (str (condp = (:ilmoitustyyppi ilmoitus)
                   :toimenpidepyynto "Toimenpidepyyntö"
                   :tiedoitus "Tiedotus"
                   (string/capitalize (name (:ilmoitustyyppi ilmoitus))))
                 " " (pvm/pvm-aika (:ilmoitettu ilmoitus)))
   :tiedot  [{:otsikko "Id" :tyyppi :string :nimi :ilmoitusid}
             {:otsikko "Ilmoitettu" :tyyppi :pvm-aika :nimi :ilmoitettu}
             {:otsikko "Otsikko" :tyyppi :string :nimi :otsikko}
             {:otsikko "Paikan kuvaus" :tyyppi :string :nimi :paikankuvaus}
             {:otsikko "Lisätietoja" :tyyppi :string :nimi :lisatieto}
             {:otsikko "Kuittaukset" :tyyppi :positiivinen-numero
              :hae     #(count (:kuittaukset ilmoitus))}]
   :data    ilmoitus})

(defmethod infopaneeli-skeema :toimenpidepyynto [ilmoitus]
  (ilmoituksen-tiedot ilmoitus))
(defmethod infopaneeli-skeema :tiedoitus [ilmoitus]
  (ilmoituksen-tiedot ilmoitus))
(defmethod infopaneeli-skeema :kysely [ilmoitus]
  (ilmoituksen-tiedot ilmoitus))


(defmethod infopaneeli-skeema :varustetoteuma [toteuma]
  {:tyyppi :varustetoteuma
   :jarjesta-fn :alkupvm
   :otsikko (str "Varustetoteuma " (pvm/pvm-aika (:alkupvm toteuma)))
   :tiedot [{:otsikko "Päivämäärä" :tyyppi :pvm :nimi :alkupvm}
            {:otsikko "Tunniste" :tyyppi :string :nimi :tunniste}
            {:otsikko "Tietolaji" :tyyppi :string :nimi :tietolaji}
            {:otsikko "Toimenpide" :tyyppi :string :nimi :toimenpide}
            {:otsikko "Kuntoluokka" :tyyppi :string :nimi :kuntoluokka}
            {:otsikko "Avaa varustekortti" :tyyppi :linkki :nimi :varustekortti-url}]
   :data toteuma})

(defmethod infopaneeli-skeema :paikkaus [paikkaus]
  (let [aloitus :aloituspvm
        paikkaus-valmis :paikkausvalmispvm
        kohde-valmis :kohdevalmispvm]
    {:tyyppi  :paikkaus
     :jarjesta-fn :aloituspvm
     :otsikko (str "Paikkauskohde " (when (aloitus paikkaus) (pvm/pvm-aika (aloitus paikkaus))))
     :tiedot  [{:otsikko "Nimi" :tyyppi :string :hae #(get-in % [:kohde :nimi])}
               {:otsikko "Tie\u00ADrekisteri\u00ADkohde" :tyyppi :string :hae #(get-in % [:kohdeosa :nimi])}
               {:osoite "Osoite" :tyyppi :tierekisteriosoite :nimi :tr}
               {:otsikko "Nykyinen päällyste" :tyyppi :string
                :hae     #(paallystys-ja-paikkaus/hae-paallyste-koodilla (:nykyinen-paallyste %))}
               {:otsikko "Toimenpide" :tyyppi :string :nimi :toimenpide}
               {:otsikko "Tila" :tyyppi :string
                :hae     #(yllapitokohteet/kuvaile-kohteen-tila (get-in % [:paikkausilmoitus :tila]))}
               (when (aloitus paikkaus)
                 {:otsikko "Aloitettu" :tyyppi :pvm-aika :nimi aloitus})
               (when (paikkaus-valmis paikkaus)
                 {:otsikko "Paikkaus valmistunut" :tyyppi :pvm-aika :nimi paikkaus-valmis})
               (when (kohde-valmis paikkaus)
                 {:otsikko "Kohde valmistunut" :tyyppi :pvm-aika :nimi kohde-valmis})]
     :data    paikkaus}))

(defmethod infopaneeli-skeema :paallystys [paallystys]
  (let [aloitus :aloituspvm
        paallystys-valmis :paallystysvalmispvm
        kohde-valmis :kohdevalmispvm]
    {:tyyppi :paallystys
     :jarjesta-fn :aloituspvm
     :otsikko "Päällystyskohde"
     :tiedot [{:otsikko "Nimi" :tyyppi :string :hae #(get-in % [:kohde :nimi])}
              {:otsikko "Tie\u00ADrekisteri\u00ADkohde" :tyyppi :string
               :hae #(get-in % [:kohdeosa :nimi])}
              {:otsikko "Osoite" :tyyppi :tierekisteriosoite :nimi :tr}
              {:otsikko "Nykyinen päällyste" :tyyppi :string
               :hae #(paallystys-ja-paikkaus/hae-paallyste-koodilla (:nykyinen-paallyste %))}
              {:otsikko "Toimenpide" :tyyppi :string :nimi :toimenpide}
              {:otsikko "Tila" :tyyppi :string
               :hae #(yllapitokohteet/kuvaile-kohteen-tila (get-in % [:paallystysilmoitus :tila]))}
              (when (aloitus paallystys)
                {:otsikko "Aloitettu" :tyyppi :pvm-aika :nimi aloitus})
              (when (paallystys-valmis paallystys)
                {:otsikko "Päällystys valmistunut" :tyyppi :pvm-aika :nimi paallystys-valmis})
              (when (kohde-valmis paallystys)
                {:otsikko "Kohde valmistunut" :tyyppi :pvm-aika :nimi kohde-valmis})]
     :data paallystys}))

(defmethod infopaneeli-skeema :turvallisuuspoikkeama [turpo]
  (let [tapahtunut :tapahtunut
        paattynyt :paattynyt
        kasitelty :kasitelty]
    {:tyyppi :turvallisuuspoikkeama
     :jarjesta-fn :tapahtunut
     :otsikko (str "Turvallisuuspoikkeama " (pvm/pvm-aika (tapahtunut turpo)))
     :tiedot [(when (tapahtunut turpo)
                {:otsikko "Tapahtunut" :tyyppi :pvm-aika :nimi tapahtunut})
              (when (kasitelty turpo)
                {:otsikko "Käsitelty" :tyyppi :pvm-aika :nimi kasitelty})
              {:otsikko "Työn\u00ADtekijä" :hae #(turpodomain/kuvaile-tyontekijan-ammatti %)}
              {:otsikko "Vammat" :hae #(turpodomain/vammat (:vammat %))}
              {:otsikko "Sairaala\u00ADvuorokaudet" :hae #(:sairaalavuorokaudet %)}
              {:otsikko "Sairaus\u00ADpoissaolo\u00ADpäivät" :tyyppi :positiivinen-numero :nimi :sairauspoissaolopaivat}
              {:otsikko "Vakavuus\u00ADaste" :hae #(turpodomain/turpo-vakavuusasteet (:vakavuusaste %))}
              {:otsikko "Kuvaus" :nimi :kuvaus}
              {:otsikko "Korjaavat toimen\u00ADpiteet"
               :hae #(str (count (filter :suoritettu (:korjaavattoimenpiteet %)))
                          "/"
                          (count (:korjaavattoimenpiteet %)))}]
     :data turpo}))

(defmethod infopaneeli-skeema :tarkastus [tarkastus]
  (let [havainnot-fn #(cond
                        (and (:havainnot %) (not-empty (:vakiohavainnot %)))
                        (str (:havainnot %) " & " (string/join ", " (:vakiohavainnot %)))

                        (:havainnot %)
                        (:havainnot %)

                        (not-empty (:vakiohavainnot %))
                        (string/join ", " (:vakiohavainnot %))

                        :default nil)]
    {:tyyppi  :tarkastus
     :jarjesta-fn :aika
     :otsikko (str (tarkastukset/+tarkastustyyppi->nimi+ (:tyyppi tarkastus))
                   (str " " (pvm/pvm-aika (:aika tarkastus))))
     :tiedot  [{:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
               {:otsikko "Tierekisteriosoite" :tyyppi :tierekisteriosoite :nimi :tierekisteriosoite}
               {:otsikko "Tarkastaja" :nimi :tarkastaja}
               {:otsikko "Havainnot" :hae havainnot-fn}]
     :data    tarkastus}))

(defmethod infopaneeli-skeema :laatupoikkeama [laatupoikkeama]
  (let [paatos #(get-in % [:paatos :paatos])
        kasittelyaika #(get-in % [:paatos :kasittelyaika])]
    {:tyyppi  :laatupoikkeama
     :jarjesta-fn :aika
     :otsikko (str "Laatupoikkeama " (pvm/pvm-aika (:aika laatupoikkeama)))
     :tiedot  [{:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
               {:otsikko "Tekijä" :hae #(str (:tekijanimi %) ", " (name (:tekija %)))}
               (when (and (paatos laatupoikkeama) (kasittelyaika laatupoikkeama))
                 {:otsikko "Päätös"
                  :hae     #(str (laatupoikkeamat/kuvaile-paatostyyppi (paatos %))
                                 " (" (pvm/pvm-aika (kasittelyaika %)) ")")})]
     :data    laatupoikkeama}))

(defmethod infopaneeli-skeema :suljettu-tieosuus [osuus]
  {:tyyppi :suljettu-tieosuus
   :jarjesta-fn :aika
   :otsikko "Suljettu tieosuus"
   :tiedot [{:otsikko "Ylläpitokohde" :hae #(str (:yllapitokohteen-nimi %) " (" (:yllapitokohteen-numero %) ")")}
            {:otsikko "Aika" :tyyppi :pvm-aika :nimi :aika}
            {:otsikko "Osoite" :tyyppi :tierekisteriosoite :nimi :tr}
            {:otsikko "Kaistat" :hae #(string/join ", " (map str (:kaistat %)))}
            {:otsikko "Ajoradat" :hae #(string/join ", " (map str (:ajoradat %)))}]
   :data osuus})

(defmethod infopaneeli-skeema :toteuma [toteuma]
  {:tyyppi  :toteuma
   :jarjesta-fn :alkanut
   :otsikko (let [toimenpiteet (map :toimenpide (:tehtavat toteuma))
                  _ (log "toteuma" (pr-str toteuma))]
              (str (if (empty? toimenpiteet)
                     "Toteuma"
                     (string/join ", " toimenpiteet))
                   (str " " (pvm/pvm-aika (:alkanut toteuma)))))
   :tiedot  (vec (concat [{:otsikko "Alkanut" :tyyppi :pvm-aika :nimi :alkanut}
                          {:otsikko "Päättynyt" :tyyppi :pvm-aika :nimi :paattynyt}
                          {:otsikko "Tierekisteriosoite" :tyyppi :tierekisteriosoite
                           :nimi    :tierekisteriosoite}
                          {:otsikko "Suorittaja" :hae #(get-in % [:suorittaja :nimi])}]

                         (for [{:keys [toimenpide maara yksikko]} (:tehtavat toteuma)]
                           {:otsikko toimenpide
                            :hae     (constantly (str maara " " yksikko))})

                         (for [materiaalitoteuma (:materiaalit toteuma)]
                           {:otsikko (get-in materiaalitoteuma [:materiaali :nimi])
                            :hae     #(str (get-in % [:materiaalit materiaalitoteuma :maara]) " "
                                           (get-in % [:materiaalit materiaalitoteuma :materiaali :yksikko]))})
                         (when (:lisatieto toteuma)
                           [{:otsikko "Lisätieto" :nimi :lisatieto}])))
   :data    toteuma})


(defmethod infopaneeli-skeema :silta [silta]
  {:tyyppi :silta
   :jarjesta-fn :tarkastusaika
   :otsikko (str "Silta " (when (:tarkastusaika silta)
                            (pvm/pvm-aika (:tarkastusaika silta))))
   :tiedot [{:otsikko "Nimi" :hae :siltanimi}
            {:otsikko "Sillan tunnus" :hae :siltatunnus}
            {:otsikko "Edellinen tarkastus" :tyyppi :pvm :hae :tarkastusaika}
            {:otsikko "Edellinen tarkastaja" :hae :tarkastaja}]
   :data silta})

(defmethod infopaneeli-skeema :tietyomaa [tietyomaa]
  {:tyyppi :tietyomaa
   :jarjesta-fn :aika
   :otsikko (str "Tietyömaa " (when (:aika tietyomaa)
                                (pvm/pvm-aika (:aika tietyomaa))))
   :tiedot [{:otsikko "Ylläpitokohde" :hae #(str (:yllapitokohteen-nimi %) " (" (:yllapitokohteen-numero %) ")")}
            {:otsikko "Aika" :hae #(pvm/pvm-aika (:aika %))}
            {:otsikko "Osoite" :hae #(tierekisteri/tierekisteriosoite-tekstina % {:teksti-tie? false})}
            {:otsikko "Kaistat" :hae #(clojure.string/join ", " (map str (:kaistat %)))}
            {:otsikko "Ajoradat" :hae #(clojure.string/join ", " (map str (:ajoradat %)))}
            {:otsikko "Nopeusrajoitus" :hae :nopeusrajoitus}]
   :data tietyomaa})

(defmethod infopaneeli-skeema :default [x]
  (log/warn "infopaneeli-skeema metodia ei implementoitu tyypille " (pr-str (:tyyppi-kartalla x))
            ", palautetaan tyhjä itemille " (pr-str x))
  nil)

(defn validoi-tieto [tieto]
  (let [otsikko (:otsikko tieto)
        skeema (remove empty? (:tiedot tieto))
        data (:data tieto)
        kenttien-arvot (mapv
                         (fn [{:keys [nimi hae] :as rivin-skeema}]
                           (if-let [get-fn (or nimi hae)]
                             [rivin-skeema (get-fn data)]
                             ;; else
                             (do (log "skeemasta puuttuu :nimi tai :hae - skeema:" (clj->js skeema) "tieto:" (clj->js tieto))
                                 [])))
                         skeema)
        tyhjat-arvot (keep (comp :otsikko first) (filter (comp nil? second) kenttien-arvot))]
    (assert (some? (:jarjesta-fn tieto)) (str "jarjesta-fn puuttuu tiedolta " (pr-str tieto)))
    (when-not (empty? tyhjat-arvot)
      (log "Yritettiin muodostaa overlayn tietoja asialle " otsikko ", mutta seuraavat tiedot puuttuivat: " (pr-str tyhjat-arvot)) "\nkenttien-arvot:" (clj->js kenttien-arvot))
    (assoc tieto :tiedot (mapv first (remove (comp nil? second) kenttien-arvot)))))

(defn validoi-tiedot [tiedot]
  (map validoi-tieto tiedot))

(defn skeemamuodossa [asiat]
  ;; FIXME: tänne tehtävä toimimaan (sort-by :jarjesta-fn)
  (->> (keep infopaneeli-skeema asiat) validoi-tiedot))
