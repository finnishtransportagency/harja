(ns harja.domain.kalustoresurssit
  "Suunnittelun kalustoresurssien hoitoluokkaryhmät MHU26-urakoille.")

(def hoitoluokkaryhmat
  "Kalustoresurssien syötössä käytetyt hoitoluokkaryhmät.
   :avain on tietokantaan tallennettava tunniste, :nimi käyttöliittymässä näytettävä teksti."
  [{:avain "ise-ib" :nimi "Ise–Ib"}
   {:avain "ic-iii" :nimi "Ic–III"}
   {:avain "k1-k2-l" :nimi "K1, K2 ja L"}])

(def hoitoluokkaryhma-avaimet
  "Sallitut hoitoluokkaryhmien tunnisteet."
  (into #{} (map :avain) hoitoluokkaryhmat))

(defn validi-hoitoluokkaryhma?
  "Onko annettu tunniste sallittu hoitoluokkaryhmä."
  [avain]
  (contains? hoitoluokkaryhma-avaimet avain))
