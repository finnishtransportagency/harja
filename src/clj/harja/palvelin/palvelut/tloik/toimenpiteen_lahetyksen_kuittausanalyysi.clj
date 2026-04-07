(ns harja.palvelin.palvelut.tloik.toimenpiteen-lahetyksen-kuittausanalyysi
	(:require [harja.domain.oikeudet :as oikeudet]
						[harja.kyselyt.konversio :as konversio]
						[harja.kyselyt.tloik.toimenpiteen-lahetyksen-kuittausanalyysi :as q]))

(def kuittausanalyysi-endpoint :hae-tloik-toimenpiteen-lahetyksen-kuittausanalyysi)

(def ryhma-limit 1000)

(defn- muodosta-hakuparametrit
	[alkaen paattyen]
	{:alkaen (konversio/sql-date alkaen)
	 :paattyen (konversio/sql-date paattyen)})

(defn- muodosta-esimerkkitapahtuma
	[tapahtuma]
	{:tapahtuma-id (:tapahtumaid tapahtuma)
	 :alkanut (:alkanut tapahtuma)
	 :ulkoinen-id (:ulkoinenid tapahtuma)})

(defn- vektoroi-tulos
	[arvo]
	(or (konversio/pgarray->vector arvo)
			(when (vector? arvo) arvo)
			(when (sequential? arvo) (vec arvo))
			[]))

(defn- muodosta-ryhma
	[esimerkkitapahtumat-ryhmittain yhteenveto]
	{:ilmoitusid (:ilmoitusid yhteenveto)
	 :kuittaustyyppi (:kuittaustyyppi yhteenveto)
	 :kanava (:kanava yhteenveto)
	 :duplikaatteja (:duplikaatteja yhteenveto)
	 :kertyneet-lahetysvirheet (:kertyneet_lahetysvirheet yhteenveto)
	 :uniikit-kuittaajat (:uniikit_kuittaajat yhteenveto)
	 :ensimmainen-alkanut (:ensimmainen_alkanut yhteenveto)
	 :viimeisin-alkanut (:viimeisin_alkanut yhteenveto)
	 :uniikit-ulkoiset-idt (vektoroi-tulos (:uniikit_ulkoiset_idt yhteenveto))
	 :esimerkkitapahtumat (mapv muodosta-esimerkkitapahtuma
														 (get esimerkkitapahtumat-ryhmittain (:ryhmaavain yhteenveto) []))})

(defn- rajaa-yhteenvedot
	[yhteenvedot]
	(let [katkaistu (< ryhma-limit (count yhteenvedot))]
		{:katkaistu katkaistu
		 :palautettavat-yhteenvedot (if katkaistu
															(take ryhma-limit yhteenvedot)
															yhteenvedot)}))

(defn- muodosta-vastaus
	[yhteenvedot tilastot esimerkkitapahtumat]
	(let [{:keys [katkaistu palautettavat-yhteenvedot]} (rajaa-yhteenvedot yhteenvedot)
			esimerkkitapahtumat-ryhmittain (group-by :ryhmaavain esimerkkitapahtumat)]
		{:ryhmat (mapv (partial muodosta-ryhma esimerkkitapahtumat-ryhmittain)
									 palautettavat-yhteenvedot)
		 :kasitellyt-rivit (:kasitellyt_rivit tilastot)
		 :ohitetut-rivit (:ohitetut_rivit tilastot)
		 :katkaistu katkaistu}))

(defn hae-toimenpiteen-lahetyksen-kuittausanalyysi
	[db kayttaja alkaen paattyen]
	(oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
	(let [hakuparametrit (muodosta-hakuparametrit alkaen paattyen)
				yhteenvetohakuparametrit (assoc hakuparametrit :limit (inc ryhma-limit))
				yhteenvedot (q/hae-duplikaattikuittausyhteenvedot db yhteenvetohakuparametrit)
				ryhmaavaimet (mapv :ryhmaavain (:palautettavat-yhteenvedot (rajaa-yhteenvedot yhteenvedot)))
				tilastot (q/hae-duplikaattikuittausten-tilastot db hakuparametrit)
				esimerkkitapahtumat (if (seq ryhmaavaimet)
															(q/hae-duplikaattikuittausten-esimerkkitapahtumat
																db
																(assoc hakuparametrit :ryhmaavaimet ryhmaavaimet))
															[])]
		(muodosta-vastaus yhteenvedot tilastot esimerkkitapahtumat)))
