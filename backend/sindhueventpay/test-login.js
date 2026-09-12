const auth = async () => {
    try {
        const res = await fetch('http://localhost:8080/api/v1/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'admin@sindhurunners.com', password: 'admin123' })
        });
        const json = await res.json();
        console.log("Status:", res.status);
        console.log("Response:", JSON.stringify(json, null, 2));
    } catch(e) {
        console.error(e);
    }
};
auth();
