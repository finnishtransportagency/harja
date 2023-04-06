(ns harja.palvelin.palvelut.yha
  "Paikallisen kannan YHA-tietojenkäsittelyn logiikka.

  YHA on päällystysurakoiden master-järjestelmä, josta haetaan Harjaan päällystyskohteet
  ja johon ne lähetetään myöhemmin takaisin."
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [taoensso.timbre :as log]
            [harja.domain.skeema :refer [Toteuma validoi]]
            [clojure.java.jdbc :as jdbc]
            [harja.kyselyt.yha :as yha-q]
            [harja.kyselyt.yllapitokohteet :as yllapitokohteet-q]
            [harja.kyselyt.konversio :as konv]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.kyselyt.paallystys-kyselyt :as paallystys-q]
            [harja.domain.oikeudet :as oikeudet]
            [harja.pvm :as pvm]
            [slingshot.slingshot :refer [try+]]
            [clj-time.core :as t]
            [harja.palvelin.palvelut.yllapitokohteet.yleiset :as yy]
            [harja.palvelin.palvelut.tierekisteri-haku :as tr-haku]
            [harja.palvelin.palvelut.yha-apurit :as yha-apurit]
            [clojure.string :as str]
            [clojure.set :as set]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.palvelin.integraatiot.vkm.vkm-komponentti :as vkm]))

(defn lukitse-urakan-yha-sidonta [db urakka-id]
  (log/info "Lukitaan urakan " urakka-id " yha-sidonta.")
  (yha-q/lukitse-urakan-yha-sidonta<! db {:urakka urakka-id}))

(defn- lisaa-urakalle-yha-tiedot [db user urakka-id {:keys [yhatunnus yhaid yhanimi elyt vuodet] :as yha-tiedot}]
  (log/info "Lisätään YHA-tiedot urakalle " urakka-id ", yhatunnus: " yhatunnus " ja yhaid: " yhaid)
  (yha-q/lisaa-urakalle-yha-tiedot<! db {:urakka urakka-id
                                         :yhatunnus yhatunnus
                                         :yhaid yhaid
                                         :yhanimi yhanimi
                                         :elyt (konv/seq->array elyt)
                                         :vuodet (konv/seq->array (map str vuodet))
                                         :kayttaja (:id user)}))

(defn- poista-urakan-yha-tiedot [db urakka-id]
  (log/debug "Poistetaan urakan " urakka-id " vanhat YHA-tiedot")
  (yha-q/poista-urakan-yha-tiedot! db {:urakka urakka-id}))

(defn- poista-urakan-yllapitokohteet [db urakka-id]
  (log/debug "Poistetaan urakan " urakka-id " ylläpitokohteet")
  (yha-q/poista-urakan-yllapitokohteet! db {:urakka urakka-id})
  (yha-q/poista-urakan-yllapitokohdeosat! db {:urakka urakka-id}))

(defn- hae-urakan-yha-tiedot [db urakka-id]
  (log/debug "Haetaan urakan " urakka-id " yha-tiedot")
  (first (into []
               (comp
                 (map #(konv/array->vec % :vuodet))
                 (map #(konv/array->vec % :elyt))
                 (map #(clojure.set/rename-keys % {:sidonta-lukittu :sidonta-lukittu?})))
               (yha-q/hae-urakan-yhatiedot db {:urakka urakka-id}))))

(defn- sido-yha-urakka-harja-urakkaan [db user {:keys [harja-urakka-id yha-tiedot]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user harja-urakka-id)
  (log/debug "Käsitellään pyyntö lisätä Harja-urakalle " harja-urakka-id " yha-tiedot: " yha-tiedot)
  (if (:sidonta-lukittu? (hae-urakan-yha-tiedot db harja-urakka-id))
    (throw (SecurityException. "Sidonta lukittu!"))
    (jdbc/with-db-transaction [db db]
      (poista-urakan-yha-tiedot db harja-urakka-id)
      (poista-urakan-yllapitokohteet db harja-urakka-id)
      (lisaa-urakalle-yha-tiedot db user harja-urakka-id yha-tiedot)
      (log/debug "YHA-tiedot sidottu. Palautetaan urakan YHA-tiedot")
      (hae-urakan-yha-tiedot db harja-urakka-id))))


(defn- hae-urakat-yhasta [db yha user {:keys [yhatunniste sampotunniste vuosi harja-urakka-id]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user harja-urakka-id)
  (let [urakat (yha/hae-urakat yha yhatunniste sampotunniste vuosi)
        yhaidt (mapv :yhaid urakat)
        sidontatiedot (when (not-empty yhaidt) (yha-q/hae-urakoiden-sidontatiedot db {:yhaidt yhaidt}))
        urakat (mapv second
                     (merge-with merge
                                 (into {} (map (juxt :yhaid identity) urakat))
                                 (into {} (map (juxt :yhaid identity) sidontatiedot))))]
    urakat))

(defn- suodata-pois-harjassa-jo-olevat-kohteet [db kohteet-yhasta]
  (let [jo-harjassa-olevat (set (map :yha-id (yha-q/hae-harjassa-olevat-yha-idt
                                               db
                                               {:annetut-yha-idt (mapv :yha-id kohteet-yhasta)})))]
    (log/debug "Harjassa ovat jo kohteet YHA id:llä " jo-harjassa-olevat)
    (filterv (fn kohteen-yha-id-ei-ole-harjassa [kohde]
               (not (jo-harjassa-olevat (:yha-id kohde))))
             kohteet-yhasta)))


(defn- merkitse-urakan-kohdeluettelo-paivitetyksi [db user harja-urakka-id]
  (log/debug "Merkitään urakan " harja-urakka-id " kohdeluettelo päivitetyksi")
  (yha-q/merkitse-urakan-yllapitokohteet-paivitetyksi<! db {:urakka harja-urakka-id
                                                            :kayttaja (:id user)}))

(defn tallenna-kohde-ja-alikohteet [db urakka-id {:keys [tierekisteriosoitevali
                                                         tunnus yha-id yha-kohdenumero alikohteet yllapitokohdetyyppi yllapitokohdetyotyyppi
                                                         nimi] :as kohde}]
  (log/debug "Tallennetaan kohde, jonka yha-id on: " yha-id (pr-str tierekisteriosoitevali))
  (let [kohde (yha-q/luo-yllapitokohde<!
                db
                {:urakka urakka-id
                 :tr_numero (:tienumero tierekisteriosoitevali)
                 :tr_alkuosa (:aosa tierekisteriosoitevali)
                 :tr_alkuetaisyys (:aet tierekisteriosoitevali)
                 :tr_loppuosa (:losa tierekisteriosoitevali)
                 :tr_loppuetaisyys (:let tierekisteriosoitevali)
                 :karttapaivamaara (:karttapaivamaara tierekisteriosoitevali)
                 :tunnus tunnus
                 :yhaid yha-id
                 :yllapitokohdetyyppi (name yllapitokohdetyyppi)
                 :yllapitokohdetyotyyppi (name yllapitokohdetyotyyppi)
                 :nimi nimi
                 :vuodet (konv/seq->array [(t/year (pvm/suomen-aikavyohykkeeseen (t/now)))])
                 :yha_kohdenumero yha-kohdenumero
                 :kohdenumero yha-kohdenumero})]
    (yllapitokohteet-q/luo-yllapitokohteelle-tyhja-aikataulu<! db {:yllapitokohde (:id kohde)})
    (yllapitokohteet-q/luo-yllapitokohteelle-tyhja-kustannustaulu<! db {:yllapitokohde (:id kohde)})
    (doseq [{:keys [sijainti tierekisteriosoitevali yha-id nimi tunnus
                    yllapitoluokka nykyinen-paallyste keskimaarainen-vuorokausiliikenne
                    paallystystoimenpide] :as alikohde} alikohteet]
      (log/debug "Tallennetaan kohteen osa, jonka yha-id on " yha-id)
      (let [uusi-kohdeosa (yha-q/luo-yllapitokohdeosa<!
                            db
                            {:yllapitokohde (:id kohde)
                             :nimi nimi
                             :tunnus tunnus
                             :tr_numero (:tienumero tierekisteriosoitevali)
                             :tr_alkuosa (:aosa tierekisteriosoitevali)
                             :tr_alkuetaisyys (:aet tierekisteriosoitevali)
                             :tr_loppuosa (:losa tierekisteriosoitevali)
                             :tr_loppuetaisyys (:let tierekisteriosoitevali)
                             :tr_ajorata (:ajorata tierekisteriosoitevali)
                             :tr_kaista (:kaista tierekisteriosoitevali)
                             :karttapaivamaara (:karttapaivamaara tierekisteriosoitevali)
                             :yllapitoluokka yllapitoluokka
                             :nykyinen_paallyste nykyinen-paallyste
                             :keskimaarainen_vuorokausiliikenne keskimaarainen-vuorokausiliikenne
                             :yhaid yha-id
                             :raekoko (:raekoko paallystystoimenpide)
                             :tyomenetelma (:paallystetyomenetelma paallystystoimenpide)
                             :massamaara (:kokonaismassamaara paallystystoimenpide)})]))))

(defn lisaa-kohteisiin-validointitiedot [db kohteet]
  (map
    (fn [{:keys [tierekisteriosoitevali alikohteet] :as kohde}]
      (let [kohteen-validointi (tr-haku/validoi-tr-osoite-tieverkolla db tierekisteriosoitevali)
            kohdeosien-validointi (map #(tr-haku/validoi-tr-osoite-tieverkolla db (:tierekisteriosoitevali %))
                                       alikohteet)]
        (assoc kohde :kohde-validi? (and (:ok? kohteen-validointi)
                                         (every? #(true? (:ok? %)) kohdeosien-validointi))
                     :kohde-epavalidi-syy
                     (first (remove nil?
                                    (concat [(:syy kohteen-validointi)]
                                            (map :syy kohdeosien-validointi)))))))
    kohteet))

(defn kohteen-tunnus [kohde teksti]
  (str "kohde-" (:yha-id kohde) (when teksti "-") teksti))

(defn alikohteen-tunnus [kohde alikohde teksti]
  (str "alikohde-" (:yha-id kohde) "-" (:yha-id alikohde) (when teksti "-") teksti))

(defn paivita-osoitteen-osa [kohde osoite avain]
  (assoc-in kohde [:tierekisteriosoitevali avain]
    (or (get osoite avain)
      (get-in kohde [:tierekisteriosoitevali avain]))))

(defn hae-vkm-osoite [vkm-kohteet hakutunnus]
  (first (filter #(= (:yha-id %) hakutunnus) vkm-kohteet)))

(defn paivita-kohde [kohde osoite tilannepvm]
  (if (some? (:virheet osoite))
    (assoc kohde :virheet (:virheet osoite))
    (-> kohde
      (assoc-in [:tierekisteriosoitevali :tilannepvm] tilannepvm)
      (paivita-osoitteen-osa osoite :tienumero)
      (paivita-osoitteen-osa osoite :ajorata)
      (paivita-osoitteen-osa osoite :aet)
      (paivita-osoitteen-osa osoite :aosa)
      (paivita-osoitteen-osa osoite :let)
      (paivita-osoitteen-osa osoite :losa))))

(defn yhdista-yha-ja-vkm-kohteet [yha-kohteet vkm-kohteet tilannepvm]
  (mapv (fn [kohde]
          (let [osoite (hae-vkm-osoite vkm-kohteet (:yha-id kohde))]
            (-> kohde
              (paivita-kohde osoite tilannepvm)
              (assoc :alikohteet
                     (mapv
                       (fn [alikohde]
                         (let [osoite (hae-vkm-osoite vkm-kohteet (:yha-id alikohde))]
                           (paivita-kohde alikohde osoite tilannepvm)))
                       (:alikohteet kohde))))))
    yha-kohteet))

(defn- paivita-yha-kohteet
  "Hakee uudet kohteet YHAsta, käyttää ne viitekehysmuuntimen läpi ja tallentaa ne harjan kantaan.
   Vaaditut tiedot:
   urakka-id:     urakan id
   tilannepvm:    pvm, jos a kohde muutetaan toiselle päivälle, esim. harjan karttapvm.
   kohdepvm:      pvm, johon kohde muutetaan viitekehysmuuntimella. Valinnainen, oletuksena nykyinen pvm.
   "
  [db yha vkm user {:keys [urakka-id tilannepvm kohdepvm]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (let [yha-kohteet (yha/hae-kohteet yha urakka-id (:kayttajanimi user))
        uudet-kohteet (suodata-pois-harjassa-jo-olevat-kohteet db yha-kohteet)
        ;; TODO: Hae kohdepvm harjan tieverkon tilannepäivämäärästä.
        kohdepvm (or kohdepvm (pvm/nyt))
        tieosoitteet (vkm/yllapitokohde->vkm-parametrit uudet-kohteet
                       (or
                         tilannepvm
                         (:karttapaivamaara (:tierekisteriosoitevali (first uudet-kohteet))))
                       kohdepvm)
        vkm-kohteet (vkm/muunna-tieosoitteet-verkolta-toiselle vkm tieosoitteet)
        yhdistetyt-kohteet (yhdista-yha-ja-vkm-kohteet uudet-kohteet vkm-kohteet kohdepvm)
        kohteet-validointitiedoilla (lisaa-kohteisiin-validointitiedot db yhdistetyt-kohteet)
        validit-kohteet (filter :kohde-validi? kohteet-validointitiedoilla)
        epavalidit-kohteet (filter (comp not :kohde-validi?) kohteet-validointitiedoilla)
        vkm-virheet (filterv :virheet yhdistetyt-kohteet)
        vkm-virheita? (not (empty? vkm-virheet))
        yha-virheita? (not (empty? epavalidit-kohteet))]
    (doseq [kohde validit-kohteet]
      (tallenna-kohde-ja-alikohteet db urakka-id kohde))
    (merkitse-urakan-kohdeluettelo-paivitetyksi db user urakka-id)
    (yy/paivita-yllapitourakan-geometria db urakka-id)
    {:status (if (or vkm-virheita? yha-virheita?)
               :error
               :ok)
     :yhatiedot (hae-urakan-yha-tiedot db urakka-id)
     :tallentamatta-jaaneet-kohteet (vec epavalidit-kohteet)
     :epaonnistuneet-vkm-muunnokset vkm-virheet
     :uudet-kohteet validit-kohteet
     :koodi (cond
              (and vkm-virheita? yha-virheita?)
              :vkm-muunnos-ja-kohteiden-tallentaminen-epaonnistui-osittain

              vkm-virheita?
              :vkm-muunnos-epaonnistui-osittain

              yha-virheita?
              :kohteiden-tallentaminen-epaonnistui-osittain

              :default
              :kohteet-tallennettu)}))

(defn laheta-kohteet-yhaan
  "Lähettää annetut kohteet teknisine tietoineen YHAan."
  [db yha user {:keys [urakka-id sopimus-id kohde-idt vuosi]}]
  (oikeudet/vaadi-oikeus "sido" oikeudet/urakat-kohdeluettelo-paallystyskohteet user urakka-id)
  (yha-apurit/tarkista-lahetettavat-kohteet db kohde-idt)
  (log/debug (format "Lähetetään kohteet: %s YHAan" kohde-idt))
  (let [lahetys (try+ (yha/laheta-kohteet yha urakka-id kohde-idt)
                      (catch [:type yha/+virhe-kohteen-lahetyksessa+] {:keys [virheet]}
                        virheet))
        lahetys-onnistui? (not (contains? lahetys :virhe))
        paivitetyt-ilmoitukset (paallystys-q/hae-urakan-paallystysilmoitukset-kohteineen db {:urakka-id urakka-id 
                                                                                             :sopimus-id sopimus-id 
                                                                                             :vuosi vuosi})]
    (merge
      {:paallystysilmoitukset paivitetyt-ilmoitukset}
      (when-not lahetys-onnistui?
        lahetys))))

(defrecord Yha []
  component/Lifecycle
  (start [this]
    (let [http (:http-palvelin this)
          db (:db this)
          yha (:yha-integraatio this)
          vkm (:vkm this)]
      (julkaise-palvelu http :sido-yha-urakka-harja-urakkaan
                        (fn [user tiedot]
                          (sido-yha-urakka-harja-urakkaan db user tiedot)))
      (julkaise-palvelu http :hae-urakat-yhasta
                        (fn [user tiedot]
                          (hae-urakat-yhasta db yha user tiedot)))
      (julkaise-palvelu http :paivita-yha-kohteet
        (fn [user tiedot]
          (paivita-yha-kohteet db yha vkm user tiedot)))
      (julkaise-palvelu http :laheta-kohteet-yhaan
                        (fn [user data]
                          (laheta-kohteet-yhaan db yha user data))))
    this)
  (stop [this]
    (poista-palvelut
      (:http-palvelin this)
      :sido-yha-urakka-harja-urakkaan
      :hae-urakat-yhasta
      :paivita-yha-kohteet
      :laheta-kohteet-yhaan)
    this))
