(ns harja.palvelin.palvelut.hallinta.lupaukset-palvelu
  (:require [com.stuartsierra.component :as component]
            [harja.kyselyt.lupaus-kyselyt :as lupaus-kyselyt]
            [harja.kyselyt.lupaus.kustannusennuste-kyselyt :as kustannusennuste-kyselyt]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.pvm :as pvm]
            [taoensso.timbre :as log]
            [clojure.set]))

(defn- hae-lupausten-linkitykset [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  {:puuttuvat-urakat (lupaus-kyselyt/hae-puuttuvat-urakka-linkitykset db)})


(defn- hae-rivin-tunnistin-selitteet [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  {:rivin-tunnistin-selitteet (lupaus-kyselyt/hae-rivin-tunnistin-selitteet db)})

(defn- hae-kategorian-urakat [db kayttaja {:keys [kategoria]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  (log/debug "hae-kategorian-urakat :: kategoria" kategoria)
  {:kategorian-urakat (lupaus-kyselyt/hae-kategorian-urakat db {:rivin-tunnistin-selite (:rivin-tunnistin-selite kategoria)
                                                                :urakan-alkuvuosi (:urakan-alkuvuosi kategoria)})})

(defn- hae-urakan-lupaukset [db kayttaja {:keys [urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  (log/debug "hae-urakan-lupaukset :: urakka-id " urakka-id)
  {:urakan-lupaukset (lupaus-kyselyt/hae-urakan-lupaukset db {:urakka-id urakka-id})})

;; TESTAUSTYÖKALUT

;; Hakee MHU-urakat joilla on kustannusennuste-lupaus
(defn- hae-urakat-kustannusennuste-testaukseen [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  (let [urakat (kustannusennuste-kyselyt/hae-urakat-joilla-kustannusennuste db)]
    {:urakat urakat}))

(defn- jsonb-kentta->map
  "Muuntaa JSONB-kentän mapiksi. Käsittelee sekä PGobject että jo valmiit mapit."
  [kentta]
  (cond
    (nil? kentta) nil
    (map? kentta) kentta  ; Jo map, palauta sellaisenaan
    :else (konversio/jsonb->clojuremap kentta)))  ; PGobject, käytä konversiota

(defn- hae-kustannusennuste-testausdata [db kayttaja {:keys [urakka-id hoitokauden-alkuvuosi]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  (let [lupaus-id (kustannusennuste-kyselyt/hae-urakan-kustannusennuste-lupaus-id
                    db {:urakka-id urakka-id})
        ;; Hae KAIKKI urakan kustannusennusteet (ilman hoitovuosisuodatusta)
        kaikki-ennusteet (kustannusennuste-kyselyt/hae-urakan-kaikki-kustannusennusteet-testaus-kaikki-hoitovuodet
                           db {:urakka-id urakka-id
                               :lupaus-id lupaus-id})
        ;; Suodata ne jotka pisteytetään tälle hoitokaudelle (huomioi offset)
        kustannusennusteet (filter (fn [ke]
                                     (let [kuukausi (:kuukausi ke)
                                           tallennuksen-hoitovuosi (:hoitovuosi ke)
                                           offset (or (kustannusennuste-kyselyt/hae-kustannusennuste-kuukausi-offset
                                                        db {:lupaus-id lupaus-id
                                                            :kuukausi kuukausi})
                                                      0)
                                           pisteytys-hoitovuosi (+ tallennuksen-hoitovuosi offset)]
                                       (= pisteytys-hoitovuosi hoitokauden-alkuvuosi)))
                             kaikki-ennusteet)
        lopputilanne (first (lupaus-kyselyt/hae-hoitovuoden-lopputilanne
                              db {:urakka-id urakka-id
                                  :hoitovuosi-alkuvuosi hoitokauden-alkuvuosi}))]
    (when-not lupaus-id
      (log/warn "VAROITUS: Urakalta" urakka-id "ei löytynyt kustannusennuste-lupausta!"))
    {:lupaus-id lupaus-id
     :kustannusennusteet (mapv (fn [ke]
                                 (-> ke
                                   (update :pisterajat jsonb-kentta->map)
                                   (update :laskentakaava_parametrit jsonb-kentta->map)
                                   (update :laskentakaava_vaiheet jsonb-kentta->map)
                                   (clojure.set/rename-keys {:laskentakaava_parametrit :laskentakaava-parametrit
                                                             :laskentakaava_vaiheet :laskentakaava-vaiheet
                                                             :laskentakaava_teksti :laskentakaava-teksti})))
                           kustannusennusteet)
     :lopputilanne (when lopputilanne
                     (assoc lopputilanne 
                            ;; Näytä vain ne kuukaudet joille pisteet on OIKEASTI laskettu
                            ;; (ei kaikkia tallennettuja kuukausia, koska offset voi vaikuttaa)
                            :kaytetyt-kuukaudet (sort (distinct (map :kuukausi 
                                                                  (filter :lasketut_pisteet kustannusennusteet))))))}))

;; Triggeröi kustannusennuste-laskennan - kutsuu lupaus_palvelu/laske-lopullinen-kustannusennuste! joka laskee lopulliset pisteet
(defn- triggeroi-kustannusennuste-laskenta [db kayttaja {:keys [urakka-id hoitokauden-alkuvuosi
                                                                 toteutunut-tavoitehinta
                                                                 toteutunut-kustannus]}]
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-lupaukset kayttaja)
  (let [user-id (:id kayttaja)
        valikatselmus-pvm (pvm/nyt)]
    ;; Kutsu olemassa olevaa funktiota
    (lupaus-palvelu/laske-lopullinen-kustannusennuste!
      db urakka-id hoitokauden-alkuvuosi
      toteutunut-tavoitehinta toteutunut-kustannus
      valikatselmus-pvm user-id)

    ;; Palauta päivitetyt tiedot
    (hae-kustannusennuste-testausdata db kayttaja
      {:urakka-id urakka-id
       :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})))

;; Hakee kustannusennusteen määräpäivät (kuukaudet) hoitokaudelle
;; Generoi määräpäivät suoraan lupauksen kirjaus-kkt kentästä
(defn- hae-kustannusennuste-maarapaivat [db kayttaja {:keys [lupaus-id hoitokauden-alkuvuosi urakka-id]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-lupaukset kayttaja)
  (let [;; Hae lupaus suoraan id:llä
        lupaus (first (lupaus-kyselyt/hae-lupaus db {:id lupaus-id}))
        
        ;; Laske hoitovuosi-nro
        urakan-alkuvuosi (:urakan-alkuvuosi lupaus)
        hoitovuosi-nro (when urakan-alkuvuosi
                        (inc (- hoitokauden-alkuvuosi urakan-alkuvuosi)))
        
        ;; Ylikirjoita hoitovuosikohtaiset arvot (sama funktio kuin päänäkymä käyttää)
        ;; HUOM: hae-lupaus palauttaa :id, mutta ylikirjoita-hoitovuosikohtaiset-arvot odottaa :lupaus-id
        lupaus-ylikirjoitettu (when hoitovuosi-nro
                                (first (lupaus-palvelu/ylikirjoita-hoitovuosikohtaiset-arvot 
                                         db [(assoc lupaus :lupaus-id (:id lupaus))] hoitovuosi-nro)))

        ;; Käytä ylikirjoitettua tai alkuperäistä
        kaytettava-lupaus (or lupaus-ylikirjoitettu lupaus)
        
        ;; Hae kirjauskuukaudet
        kirjaus-kkt (sort (:kirjaus-kkt kaytettava-lupaus))
        
        ;; Generoi määräpäivät kirjauskuukausista
        ;; Käytetään kiinteää 15. päivää (kuten oikeassa kirjauksessa)
        maarapaivat (mapv (fn [kuukausi]
                            (let [vuosi (if (>= kuukausi 10)
                                          hoitokauden-alkuvuosi
                                          (inc hoitokauden-alkuvuosi))]
                              {:kuukausi kuukausi
                               :vuosi vuosi
                               :paiva 15
                               :kuvaus (str "Kuukausi " kuukausi)}))
                      kirjaus-kkt)]
    
    (log/info "Generoitiin määräpäivät lupaus-id:" lupaus-id
              "hoitovuosi-nro:" hoitovuosi-nro
              "kirjaus-kkt:" kirjaus-kkt
              "määräpäivät:" (count maarapaivat) "kpl")
    {:maarapaivat maarapaivat}))

(defn- poista-kustannusennusteet-testaukseen
  [db kayttaja {:keys [urakka-id hoitokauden-alkuvuosi]}]
  (log/info "Poistetaan testikustannusennusteet urakalta" urakka-id 
            "hoitokaudelta" hoitokauden-alkuvuosi)
  (oikeudet/vaadi-kirjoitusoikeus oikeudet/hallinta-lupaukset kayttaja)
  
  ;; Ensin lasketaan kuinka monta kustannusennustetta poistetaan
  (let [poistetut-result (kustannusennuste-kyselyt/hae-poistettavien-kustannusennusteiden-lkm 
                          db 
                          {:urakka-id urakka-id
                           :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
        poistettu-kpl (:kpl poistetut-result)]
    
    ;; Sitten poistetaan ne
    (kustannusennuste-kyselyt/poista-urakan-hoitokauden-kustannusennusteet! 
     db 
     {:urakka-id urakka-id
      :hoitokauden-alkuvuosi hoitokauden-alkuvuosi})
    
    (log/info "Poistettu" poistettu-kpl "kustannusennustetta")
    {:onnistui true
     :poistettu-kpl poistettu-kpl}))

(defrecord LupauksetHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db] :as this}] 
    (julkaise-palvelu http-palvelin :hae-lupausten-linkitykset
      (fn [kayttaja _tiedot]
        (hae-lupausten-linkitykset db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-rivin-tunnistin-selitteet
      (fn [kayttaja _tiedot]
        (hae-rivin-tunnistin-selitteet db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-kategorian-urakat
      (fn [kayttaja tiedot]
        (hae-kategorian-urakat db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :hae-urakan-lupaukset
      (fn [kayttaja tiedot]
        (hae-urakan-lupaukset db kayttaja tiedot)))
    
    ;; Testaustyökalut
    (julkaise-palvelu http-palvelin :hae-urakat-kustannusennuste-testaukseen
      (fn [kayttaja _tiedot]
        (hae-urakat-kustannusennuste-testaukseen db kayttaja)))
    (julkaise-palvelu http-palvelin :hae-kustannusennuste-testausdata
      (fn [kayttaja tiedot]
        (hae-kustannusennuste-testausdata db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :hae-kustannusennuste-maarapaivat
      (fn [kayttaja tiedot]
        (hae-kustannusennuste-maarapaivat db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :triggeroi-kustannusennuste-laskenta
      (fn [kayttaja tiedot]
        (triggeroi-kustannusennuste-laskenta db kayttaja tiedot)))
    (julkaise-palvelu http-palvelin :poista-kustannusennusteet-testaukseen
      (fn [kayttaja tiedot]
        (poista-kustannusennusteet-testaukseen db kayttaja tiedot)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-lupausten-linkitykset
      :hae-rivin-tunnistin-selitteet
      :hae-kategorian-urakat
      :hae-urakan-lupaukset
      ;; Testaustyökalut
      :hae-urakat-kustannusennuste-testaukseen
      :hae-kustannusennuste-testausdata
      :hae-kustannusennuste-maarapaivat
      :triggeroi-kustannusennuste-laskenta
      :poista-kustannusennusteet-testaukseen)
    this))