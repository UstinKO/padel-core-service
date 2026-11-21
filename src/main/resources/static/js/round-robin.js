(() => {
  const send = async (form, url, body) => {
    const button = form.querySelector('button[type="submit"]');
    const error = form.querySelector("[data-rr-error]");
    button.disabled = true;
    error.textContent = "";
    try {
      const response = await conductFetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
        },
        body: JSON.stringify(body),
      });
      const data = await response.json();
      if (!response.ok || data.success === false) {
        error.textContent = data.detail || form.dataset.error;
        button.disabled = false;
        return;
      }
      window.location.reload();
    } catch (failure) {
      error.textContent = form.dataset.error;
      button.disabled = false;
    }
  };
  document.querySelectorAll("[data-rr-score]").forEach((form) =>
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const body = Object.fromEntries(new FormData(form));
      send(
        form,
        `/tournaments/team-americano/api/round-robin/matches/${form.dataset.id}/result`,
        {
          team1Score: Number(body.team1Score),
          team2Score: Number(body.team2Score),
        },
      );
    }),
  );
  document.querySelectorAll("[data-rr-add]").forEach((form) => {
    form.elements.requestId.value = crypto.randomUUID();
    form.addEventListener("submit", (event) => {
      event.preventDefault();
      const body = Object.fromEntries(new FormData(form));
      send(form, form.dataset.url, {
        team1Id: Number(body.team1Id),
        team2Id: Number(body.team2Id),
        courtNumber: Number(body.courtNumber),
        placementStart: body.placementStart
          ? Number(body.placementStart)
          : null,
        label: body.label,
        requestId: body.requestId,
      });
    });
  });
})();
